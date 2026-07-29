package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.kafka.EventEnvelope
import com.loopers.kafka.MalformedEventException
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class MetricsEventHandlerTest {
    private val facade = mockk<ProductMetricsFacade>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val handler = MetricsEventHandler(facade, objectMapper)

    private val occurredAt = LocalDateTime.of(2026, 7, 16, 10, 0)

    private fun envelope(eventType: String, aggregateId: String, eventId: UUID, payload: String = "{}") =
        EventEnvelope(
            eventId = eventId.toString(),
            eventType = eventType,
            aggregateType = if (eventType.startsWith("ORDER")) "ORDER" else "PRODUCT",
            aggregateId = aggregateId,
            occurredAt = occurredAt,
            payload = objectMapper.readTree(payload),
        )

    @Test
    fun `LIKE_CREATED 는 좋아요 증가로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CREATED", aggregateId = "7", eventId = eventId))

        verify { facade.increaseLike(eventId, 7L, occurredAt) }
    }

    @Test
    fun `LIKE_CANCELED 는 좋아요 감소로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CANCELED", aggregateId = "7", eventId = eventId))

        verify { facade.decreaseLike(eventId, 7L, occurredAt) }
    }

    @Test
    fun `PRODUCT_VIEWED 는 조회 증가로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("PRODUCT_VIEWED", aggregateId = "7", eventId = eventId))

        verify { facade.increaseView(eventId, 7L, occurredAt) }
    }

    @Test
    fun `ORDER_PAID 는 payload 의 라인을 판매량 누적으로 번역된다`() {
        val eventId = UUID.randomUUID()
        val payload = """{"lines":[{"productId":7,"quantity":2},{"productId":9,"quantity":1}]}"""

        handler.handle(envelope("ORDER_PAID", aggregateId = "100", eventId = eventId, payload = payload))

        verify {
            facade.addSales(eventId, listOf(SalesLine(7L, 2), SalesLine(9L, 1)), occurredAt)
        }
    }

    @Test
    fun `PRODUCT_DELETED 는 시간별 집계 정리로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("PRODUCT_DELETED", aggregateId = "7", eventId = eventId))

        verify { facade.removeProduct(eventId, 7L) }
    }

    @Test
    fun `ORDER_CREATED 는 판매량으로 집계하지 않는다 - 결제 미확정 주문은 판매가 아니다`() {
        val payload = """{"lines":[{"productId":7,"quantity":2}]}"""

        handler.handle(envelope("ORDER_CREATED", aggregateId = "100", eventId = UUID.randomUUID(), payload = payload))

        verify(exactly = 0) { facade.addSales(any(), any(), any()) }
    }

    @Test
    fun `역직렬화할 수 없는 메시지는 MalformedEventException 을 던진다 - 재시도 없이 DLT 로 격리된다`() {
        assertThrows<MalformedEventException> { handler.handle("not-json".toByteArray()) }

        verify(exactly = 0) { facade.increaseLike(any(), any(), any()) }
        verify(exactly = 0) { facade.decreaseLike(any(), any(), any()) }
        verify(exactly = 0) { facade.increaseView(any(), any(), any()) }
        verify(exactly = 0) { facade.addSales(any(), any(), any()) }
    }

    @Test
    fun `aggregateId 가 상품 식별자가 아니면 MalformedEventException 을 던진다`() {
        assertThrows<MalformedEventException> {
            handler.handle(envelope("LIKE_CREATED", aggregateId = "not-a-number", eventId = UUID.randomUUID()))
        }

        verify(exactly = 0) { facade.increaseLike(any(), any(), any()) }
    }

    @Test
    fun `ORDER_PAID payload 에 lines 배열이 없으면 MalformedEventException 을 던진다`() {
        assertThrows<MalformedEventException> {
            handler.handle(envelope("ORDER_PAID", aggregateId = "100", eventId = UUID.randomUUID(), payload = "{}"))
        }

        verify(exactly = 0) { facade.addSales(any(), any(), any()) }
    }
}
