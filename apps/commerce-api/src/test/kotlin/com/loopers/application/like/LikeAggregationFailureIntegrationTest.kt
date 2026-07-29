package com.loopers.application.like

import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.like.LikeRepository
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/**
 * 집계 실패 격리 — 좋아요 수 집계(`LikeCountUpdater`)가 실패해도 좋아요 자체는 성공(커밋)해야 한다.
 * 리스너는 커밋 이후 별도 스레드에서 돌므로, 그 실패가 좋아요 트랜잭션·사용자 응답에 전파되지 않는다(eventual consistency).
 */
@SpringBootTest
class LikeAggregationFailureIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var likeCountUpdater: LikeCountUpdater

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `집계가 실패해도 좋아요는 커밋된다`() {
        val product = productRepository.save(ProductFixture.validProduct(likeCount = 0L))
        every { likeCountUpdater.increase(any()) } throws RuntimeException("집계 실패 주입")

        likeFacade.like(userId = 1L, productId = product.id)

        // 좋아요 행은 정상 저장(커밋)된다 — 집계 실패와 무관하게 사용자에게는 성공.
        assertThat(likeRepository.existsByUserIdAndProductId(1L, product.id)).isTrue()
        // 리스너가 실제로 집계를 시도(그리고 실패)했음을 확인한다.
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            verify { likeCountUpdater.increase(product.id) }
        }
        // 집계는 반영되지 않아 카운트는 0 으로 남는다(추후 재집계/재처리 대상).
        assertThat(productRepository.findById(product.id)!!.likeCount).isEqualTo(0L)
    }
}
