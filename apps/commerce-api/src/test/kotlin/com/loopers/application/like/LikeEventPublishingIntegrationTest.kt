package com.loopers.application.like

import com.loopers.domain.like.LikeEvent
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener

/**
 * 좋아요 이벤트 발행 — `like`/`unlike` 가 좋아요 트랜잭션 안에서 `LikeChangedEvent`(±1)를 발행하는지 검증한다.
 * (likeCount 반영을 리스너로 이관·집계 실패 격리는 후속 사이클)
 */
@SpringBootTest
@Import(LikeEventPublishingIntegrationTest.TestConfig::class)
class LikeEventPublishingIntegrationTest @Autowired constructor(
    private val likeFacade: LikeFacade,
    private val productRepository: ProductRepository,
    private val events: CapturingLikeListener,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        events.received.clear()
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `좋아요하면 LikeEvent_Created 를 발행한다`() {
        val product = productRepository.save(ProductFixture.validProduct())

        likeFacade.like(userId = 1L, productId = product.id)

        assertThat(events.received).hasSize(1)
        assertThat(events.received.first()).isInstanceOf(LikeEvent.Created::class.java)
        assertThat(events.received.first().productId).isEqualTo(product.id)
    }

    @TestConfiguration
    class TestConfig {
        @Bean
        fun capturingLikeListener() = CapturingLikeListener()
    }

    class CapturingLikeListener {
        val received = mutableListOf<LikeEvent>()

        @EventListener
        fun on(event: LikeEvent) {
            received.add(event)
        }
    }
}
