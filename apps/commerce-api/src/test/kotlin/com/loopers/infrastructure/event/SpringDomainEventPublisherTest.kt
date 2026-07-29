package com.loopers.infrastructure.event

import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.like.LikeEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.EventListener

/**
 * 이벤트 발행 메커니즘 검증 — 포트(`DomainEventPublisher`)로 발행한 도메인 이벤트가
 * 해당 타입 리스너에게 그대로 전달되는지 확인한다. (likeCount 반영·AFTER_COMMIT·@Async 는 후속 사이클)
 */
@SpringBootTest
@Import(SpringDomainEventPublisherTest.TestConfig::class)
class SpringDomainEventPublisherTest @Autowired constructor(
    private val publisher: DomainEventPublisher,
    private val listener: CapturingLikeListener,
) {
    @Test
    fun `publish 하면 LikeEvent 리스너가 호출된다`() {
        val event = LikeEvent.Created(productId = 1L)

        publisher.publish(event)

        assertThat(listener.received).containsExactly(event)
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
