package com.loopers.infrastructure.outbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

/**
 * Outbox 레코드의 실패 전이 규칙 — 실패를 기록할 때마다 재시도 횟수를 세고 지수 백오프를 걸며,
 * 상한을 소진하면 FAILED 로 격리해 폴링 대상에서 제외한다(발행측 DLQ).
 */
class OutboxEventEntityTest {
    @Test
    fun `실패를 기록하면 재시도 횟수가 늘고 다음 재시도 시각에 지수 백오프가 걸린다`() {
        val event = outboxRow()
        val at = LocalDateTime.of(2026, 7, 8, 12, 0, 0)

        event.recordFailure(at, "broker down")

        assertThat(event.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(event.retryCount).isEqualTo(1)
        assertThat(event.nextRetryAt).isEqualTo(at.plusSeconds(1))

        event.recordFailure(at, "broker down")
        assertThat(event.nextRetryAt).isEqualTo(at.plusSeconds(2))

        event.recordFailure(at, "broker down")
        assertThat(event.nextRetryAt).isEqualTo(at.plusSeconds(4))
    }

    @Test
    fun `재시도 상한을 소진하면 FAILED 로 격리된다`() {
        val event = outboxRow()
        val at = LocalDateTime.of(2026, 7, 8, 12, 0, 0)

        repeat(9) { event.recordFailure(at, "broker down") }
        assertThat(event.status).isEqualTo(OutboxStatus.PENDING)

        event.recordFailure(at, "broker down")
        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(event.retryCount).isEqualTo(10)
    }

    @Test
    fun `백오프 시각 전에는 재시도 대기 상태다`() {
        val event = outboxRow()
        val at = LocalDateTime.of(2026, 7, 8, 12, 0, 0)
        event.recordFailure(at, "broker down") // nextRetryAt = at + 1s

        assertThat(event.isAwaitingRetry(at)).isTrue()
        assertThat(event.isAwaitingRetry(at.plusSeconds(1))).isFalse()
    }

    @Test
    fun `비재시도성 실패는 재시도 없이 즉시 FAILED 로 격리된다`() {
        val event = outboxRow()

        event.failPermanently("unknown aggregateType for outbox routing: X")

        assertThat(event.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(event.lastError).contains("unknown aggregateType")
    }

    @Test
    fun `실패 기록은 원인 메시지를 남긴다`() {
        val event = outboxRow()

        event.recordFailure(LocalDateTime.now(), "x".repeat(1000))

        assertThat(event.lastError).hasSize(500)
    }

    private fun outboxRow() = OutboxEventEntity.create(
        eventId = UUID.randomUUID(),
        aggregateType = "ORDER",
        aggregateId = "42",
        eventType = "ORDER_CREATED",
        payload = "{}",
        occurredAt = LocalDateTime.now(),
    )
}
