package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.support.event.ExternalEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Outbox 브리지 — 외부 전파 이벤트(`ExternalEvent`)를 커밋 직전(BEFORE_COMMIT) 같은 트랜잭션에서 아웃박스에 적재한다.
 * 도메인 변경과 아웃박스 기록이 원자적으로 커밋되어 dual-write 유실 창을 없앤다(At Least Once 의 토대).
 * outbox 적재는 비즈니스 유즈케이스가 아닌 메시징 메커니즘의 기술적 부수효과이므로, application 을 거치지 않고 인프라에 둔다.
 */
@Component
class OutboxEventListener(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val objectMapper: ObjectMapper,
) {
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun on(event: ExternalEvent) {
        outboxEventJpaRepository.save(
            OutboxEventEntity.create(
                eventId = event.eventId,
                aggregateType = event.aggregateType,
                aggregateId = event.aggregateId,
                eventType = event.eventType,
                payload = objectMapper.writeValueAsString(event),
                occurredAt = event.occurredAt,
            ),
        )
    }
}
