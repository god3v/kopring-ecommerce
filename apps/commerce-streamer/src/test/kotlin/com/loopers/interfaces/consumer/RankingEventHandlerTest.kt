package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.ranking.RankingSignal
import com.loopers.kafka.EventEnvelope
import com.loopers.kafka.MalformedEventException
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.UUID

class RankingEventHandlerTest {
    private val rankingFacade = mockk<RankingFacade>(relaxed = true)
    private val objectMapper = ObjectMapper()
    private val handler = RankingEventHandler(rankingFacade, objectMapper)

    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)

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
    fun `PRODUCT_VIEWED 는 조회 신호 반영으로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("PRODUCT_VIEWED", aggregateId = "7", eventId = eventId))

        verify { rankingFacade.reflect(eventId, RankingSignal.VIEW, 7L, 1, occurredAt) }
    }

    @Test
    fun `LIKE_CREATED 는 좋아요 신호 반영으로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CREATED", aggregateId = "7", eventId = eventId))

        verify { rankingFacade.reflect(eventId, RankingSignal.LIKE, 7L, 1, occurredAt) }
    }

    @Test
    fun `LIKE_CANCELED 는 좋아요 취소 신호 반영으로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("LIKE_CANCELED", aggregateId = "7", eventId = eventId))

        verify { rankingFacade.reflect(eventId, RankingSignal.LIKE_CANCEL, 7L, 1, occurredAt) }
    }

    @Test
    fun `ORDER_PAID 는 라인마다 주문 신호 반영으로 번역된다`() {
        val eventId = UUID.randomUUID()
        val payload = """{"lines":[{"productId":7,"quantity":2},{"productId":9,"quantity":1}]}"""

        handler.handle(envelope("ORDER_PAID", aggregateId = "100", eventId = eventId, payload = payload))

        verify { rankingFacade.reflect(eventId, RankingSignal.ORDER, 7L, 2, occurredAt) }
        verify { rankingFacade.reflect(eventId, RankingSignal.ORDER, 9L, 1, occurredAt) }
    }

    @Test
    fun `PRODUCT_DELETED 는 랭킹판 제거로 번역된다`() {
        val eventId = UUID.randomUUID()

        handler.handle(envelope("PRODUCT_DELETED", aggregateId = "7", eventId = eventId))

        verify { rankingFacade.removeProduct(7L, occurredAt) }
    }

    @Test
    fun `역직렬화할 수 없는 메시지는 MalformedEventException 을 던진다`() {
        assertThrows<MalformedEventException> { handler.handle("not-json".toByteArray()) }

        verify(exactly = 0) { rankingFacade.reflect(any(), any(), any(), any(), any()) }
    }
}
