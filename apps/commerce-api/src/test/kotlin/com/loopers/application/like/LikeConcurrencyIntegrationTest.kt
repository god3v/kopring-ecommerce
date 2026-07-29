package com.loopers.application.like

import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 좋아요 동시성 — 좋아요 수 캐시(`products.like_count`)가 원자 증감(+ 취소 시 삭제행 게이팅)으로
 * 동시/중복 요청에도 정확히 집계되는지 실제 영속 계층으로 검증한다.
 * 집계는 `LikeChangedEvent` 를 받은 리스너가 커밋 이후 비동기로 반영하므로, 카운트는 최종 일관성으로 수렴한다(await).
 *
 * test 프로파일 기본 커넥션 풀은 10 이라, 비관 락/원자 UPDATE 대기 중 커넥션을 점유하는 다수 스레드를
 * 충분히 돌리기 어렵다. 이 동시성 스펙에 한해 풀을 키워(테스트 한정) "여러명"을 재현한다.
 */
@SpringBootTest(
    properties = [
        "datasource.mysql-jpa.main.maximum-pool-size=32",
        "datasource.mysql-jpa.main.minimum-idle=10",
    ],
)
class LikeConcurrencyIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun runConcurrently(threads: Int, block: (Int) -> Unit) {
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) { i ->
            executor.submit {
                try {
                    startLatch.await()
                    block(i)
                } catch (_: Exception) {
                    // 같은 사용자 중복 like 등에서 유니크 위반이 날 수 있다 — 카운트 집계 정확성이 목적이므로 무시.
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
    }

    @DisplayName("[1] 서로 다른 사용자가 같은 상품에 동시에 like 하면, 좋아요 수가 인원수만큼 정확히 집계된다.")
    @Test
    fun concurrentLikesByDifferentUsersAggregateCount() {
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", likeCount = 0L))
        val users = 16

        runConcurrently(users) { i -> likeFacade.like(userId = (i + 1).toLong(), productId = product.id) }

        awaitLikeCount(product.id, users.toLong())
    }

    @DisplayName("[2] 서로 다른 사용자가 같은 상품에 동시에 unlike 하면, 좋아요 수가 0 까지 정확히 차감된다.")
    @Test
    fun concurrentUnlikesByDifferentUsersAggregateCount() {
        val users = 16
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", likeCount = users.toLong()))
        (1..users).forEach { likeRepository.save(Like.create(userId = it.toLong(), productId = product.id)) }

        runConcurrently(users) { i -> likeFacade.unlike(userId = (i + 1).toLong(), productId = product.id) }

        awaitLikeCount(product.id, 0L)
        assertThat((1..users).none { likeRepository.existsByUserIdAndProductId(it.toLong(), product.id) }).isTrue()
    }

    @DisplayName("[3] 같은 사용자가 같은 상품에 동시에 여러 번 like 해도, 좋아요 수는 정확히 1 만 올라간다.")
    @Test
    fun concurrentLikesBySameUserIncreaseByOne() {
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", likeCount = 0L))
        val userId = 7L

        runConcurrently(16) { likeFacade.like(userId = userId, productId = product.id) }

        // 유니크 제약 + 원자 증감 — 승자 1건만 저장·발행하고 패자는 롤백되어(발행 없음) 카운트가 정확히 1 로 수렴.
        awaitLikeCount(product.id, 1L)
        assertThat(likeRepository.existsByUserIdAndProductId(userId, product.id)).isTrue()
    }

    @DisplayName("[4] 같은 사용자가 같은 상품에 동시에 여러 번 unlike 해도, 좋아요 수는 정확히 1 만 내려간다.")
    @Test
    fun concurrentUnlikesBySameUserDecreaseByOne() {
        // 다른 사용자들의 좋아요를 포함한 초기 카운트(5) 에, 이 사용자의 좋아요 행 1건만 존재한다.
        val initial = 5L
        val userId = 7L
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", likeCount = initial))
        likeRepository.save(Like.create(userId = userId, productId = product.id))

        runConcurrently(16) { likeFacade.unlike(userId = userId, productId = product.id) }

        // 삭제행 게이팅(`if delete > 0`) 으로 실제 삭제한 승자만 -1 발행, 패자(삭제 0건)는 발행하지 않는다.
        awaitLikeCount(product.id, initial - 1)
        assertThat(likeRepository.existsByUserIdAndProductId(userId, product.id)).isFalse()
    }

    private fun awaitLikeCount(productId: Long, expected: Long) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(productRepository.findById(productId)!!.likeCount).isEqualTo(expected)
        }
    }
}
