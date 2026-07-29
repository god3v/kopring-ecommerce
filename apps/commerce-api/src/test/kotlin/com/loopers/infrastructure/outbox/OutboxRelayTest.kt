package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.kafka.EventEnvelope
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Outbox 릴레이 — 미발행(PENDING) 아웃박스를 aggregateType 별 토픽에 aggregateId 를 key(파티션 순서 보장)로,
 * `EventEnvelope` 봉투로 감싸 발행하고, 브로커 ack 이후에만 PUBLISHED 로 전이한다.
 * 발행 실패 시 PENDING 을 유지해 다음 릴레이가 재시도하고(At Least Once),
 * 실패한 애그리거트의 뒤 이벤트는 같은 주기에 발행하지 않아 파티션 내 순서를 지킨다.
 */
class OutboxRelayTest {
    private val outboxEventJpaRepository = mockk<OutboxEventJpaRepository>(relaxed = true)
    private val kafkaTemplate = mockk<KafkaTemplate<Any, Any>>()
    private val objectMapper = ObjectMapper()
    private val meterRegistry = SimpleMeterRegistry()
    private val relay = OutboxRelay(outboxEventJpaRepository, kafkaTemplate, objectMapper, meterRegistry)

    @Test
    fun `PENDING 아웃박스를 aggregateType 별 토픽에 aggregateId key 로 봉투에 담아 발행하고 PUBLISHED 로 전이한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = """{"orderId":42,"userId":1}""")
        val like = outboxRow(aggregateType = "PRODUCT", aggregateId = "7", eventType = "LIKE_CREATED", payload = """{"productId":7}""")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(order, like)
        val envelopes = mutableListOf<Any>()
        every { kafkaTemplate.send(any<String>(), any(), capture(envelopes)) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        verify { kafkaTemplate.send("order-events", "42", any()) }
        verify { kafkaTemplate.send("catalog-events", "7", any()) }
        val orderEnvelope = envelopes.map { it as EventEnvelope }.first { it.aggregateId == "42" }
        assertThat(orderEnvelope.eventType).isEqualTo("ORDER_CREATED")
        assertThat(orderEnvelope.aggregateType).isEqualTo("ORDER")
        assertThat(orderEnvelope.payload.get("orderId").asInt()).isEqualTo(42)
        assertThat(order.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(order.publishedAt).isNotNull()
        assertThat(like.status).isEqualTo(OutboxStatus.PUBLISHED)
    }

    @Test
    fun `발행이 실패하면 실패를 기록하고 PENDING 으로 남겨 다음 릴레이에서 재시도한다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(order)
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")

        relay.relay()

        assertThat(order.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(order.publishedAt).isNull()
        assertThat(order.retryCount).isEqualTo(1)
        assertThat(order.nextRetryAt).isNotNull()
    }

    @Test
    fun `백오프 대기 중인 이벤트는 발행을 시도하지 않고, 같은 애그리거트의 뒤 이벤트도 건너뛴다 - 순서 보존`() {
        val first = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        val second = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        val other = outboxRow(aggregateType = "PRODUCT", aggregateId = "7", eventType = "LIKE_CREATED", payload = "{}")
        first.recordFailure(LocalDateTime.now(), "broker down") // nextRetryAt 이 미래 → 대기 중
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(first, second, other)
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        // 대기 중인 애그리거트(주문 42)는 두 건 모두 시도조차 하지 않고, 무관한 애그리거트만 발행된다.
        verify(exactly = 0) { kafkaTemplate.send("order-events", "42", any()) }
        verify(exactly = 1) { kafkaTemplate.send("catalog-events", "7", any()) }
        assertThat(first.retryCount).isEqualTo(1) // 건너뛰기는 실패 기록이 아니다
        assertThat(second.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(other.status).isEqualTo(OutboxStatus.PUBLISHED)
    }

    @Test
    fun `재시도 상한을 소진한 실패는 FAILED 로 격리되고 지표를 남긴다`() {
        val order = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        repeat(9) { order.recordFailure(LocalDateTime.now().minusHours(1), "broker down") } // 백오프는 소진된 과거
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(order)
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")

        relay.relay()

        assertThat(order.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(meterRegistry.counter(OutboxRelay.METRIC_FAILED).count()).isEqualTo(1.0)
    }

    @Test
    fun `라우팅할 수 없는 aggregateType 은 재시도 없이 즉시 FAILED 로 격리하고, 뒤 이벤트는 발행된다`() {
        val unroutable = outboxRow(aggregateType = "UNKNOWN", aggregateId = "1", eventType = "X", payload = "{}")
        val other = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(unroutable, other)
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        // 몇 번을 재시도해도 같은 결과인 오류 — 백오프를 태우지 않고 바로 격리한다
        assertThat(unroutable.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(unroutable.retryCount).isEqualTo(0)
        assertThat(unroutable.lastError).contains("unknown aggregateType")
        assertThat(other.status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(meterRegistry.counter(OutboxRelay.METRIC_FAILED).count()).isEqualTo(1.0)
    }

    @Test
    fun `payload 를 봉투로 만들 수 없으면 재시도 없이 즉시 FAILED 로 격리한다`() {
        val broken = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "not-json{{")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(broken)
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())

        relay.relay()

        assertThat(broken.status).isEqualTo(OutboxStatus.FAILED)
        assertThat(broken.retryCount).isEqualTo(0)
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any(), any()) }
    }

    @Test
    fun `같은 애그리거트의 앞선 이벤트가 실패하면 뒤 이벤트는 이번 주기에 발행하지 않는다 - 파티션 내 순서 보존`() {
        val first = outboxRow(aggregateType = "COUPON_ISSUE_REQUEST", aggregateId = "1", eventType = "COUPON_ISSUE_REQUESTED", payload = "{}")
        val second = outboxRow(aggregateType = "COUPON_ISSUE_REQUEST", aggregateId = "1", eventType = "COUPON_ISSUE_REQUESTED", payload = "{}")
        val other = outboxRow(aggregateType = "ORDER", aggregateId = "42", eventType = "ORDER_CREATED", payload = "{}")
        every { outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, any()) } returns listOf(first, second, other)
        // 첫 이벤트만 실패하고 이후 호출은 성공하도록 스텁 — 뒤 이벤트가 발행됐다면 순서가 뒤집혔을 상황.
        var calls = 0
        every { kafkaTemplate.send(any<String>(), any(), any()) } answers {
            calls++
            if (calls == 1) throw RuntimeException("broker down") else CompletableFuture.completedFuture(mockk())
        }

        relay.relay()

        // 실패한 애그리거트(쿠폰 1)의 두 이벤트는 모두 PENDING, 무관한 애그리거트(주문 42)는 발행된다.
        assertThat(first.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(second.status).isEqualTo(OutboxStatus.PENDING)
        assertThat(other.status).isEqualTo(OutboxStatus.PUBLISHED)
        verify(exactly = 0) { kafkaTemplate.send("coupon-issue-requests", "1", match { (it as EventEnvelope).eventId == second.eventId }) }
    }

    private fun outboxRow(aggregateType: String, aggregateId: String, eventType: String, payload: String) =
        OutboxEventEntity.create(
            eventId = UUID.randomUUID(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            occurredAt = LocalDateTime.now(),
        )
}
