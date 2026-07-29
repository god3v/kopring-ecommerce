package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.ranking.RankingFacade
import com.loopers.domain.ranking.RankingSignal
import com.loopers.kafka.EventEnvelope
import com.loopers.kafka.MalformedEventException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Kafka 메시지를 랭킹 신호로 번역해 반영 연산으로 넘긴다.
 * eventType 문자열과 와이어 포맷을 아는 것은 여기(interfaces)까지다 — application 은 이벤트 종류를 모르는 연산만 노출한다.
 * 멱등 키(eventId)와 발생 시각(occurredAt)을 그대로 전달해, 재소비·날짜 귀속이 정확히 처리되게 한다.
 *
 * 형식이 깨진 메시지는 재전달해도 영영 실패하므로 MalformedEventException 으로 던진다 — 에러 핸들러가 재시도 없이 DLT 로 격리한다.
 * 반영(Redis) 실패는 일시 장애일 수 있어 예외를 전파해 재전달로 복구한다.
 */
@Component
class RankingEventHandler(
    private val rankingFacade: RankingFacade,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(RankingEventHandler::class.java)

    fun handle(message: ByteArray) {
        val envelope = runCatching { objectMapper.readValue(message, EventEnvelope::class.java) }
            .getOrElse { e -> throw MalformedEventException("역직렬화할 수 없는 메시지", e) }
        handle(envelope)
    }

    fun handle(envelope: EventEnvelope) {
        val eventId = envelope.eventId.toUuidOrNull()
            ?: throw malformed(envelope, "eventId 가 UUID 형식이 아니다")
        val occurredAt = envelope.occurredAt
        when (envelope.eventType) {
            "PRODUCT_VIEWED" -> rankingFacade.reflect(eventId, RankingSignal.VIEW, productIdOf(envelope), 1, occurredAt)
            "LIKE_CREATED" -> rankingFacade.reflect(eventId, RankingSignal.LIKE, productIdOf(envelope), 1, occurredAt)
            "LIKE_CANCELED" -> rankingFacade.reflect(eventId, RankingSignal.LIKE_CANCEL, productIdOf(envelope), 1, occurredAt)
            "ORDER_PAID" -> orderLinesOf(envelope).forEach { line ->
                rankingFacade.reflect(eventId, RankingSignal.ORDER, line.productId, line.quantity, occurredAt)
            }
            "PRODUCT_DELETED" -> rankingFacade.removeProduct(productIdOf(envelope), occurredAt)
            else -> log.debug("랭킹 반영 대상이 아닌 이벤트 타입: {}", envelope.eventType)
        }
    }

    private fun productIdOf(envelope: EventEnvelope): Long =
        envelope.aggregateId.toLongOrNull()
            ?: throw malformed(envelope, "aggregateId 가 상품 식별자가 아니다")

    private fun orderLinesOf(envelope: EventEnvelope): List<OrderLine> {
        val lines = envelope.payload.path("lines")
        if (!lines.isArray) {
            throw malformed(envelope, "payload.lines 가 배열이 아니다")
        }
        return lines.map { line ->
            val productId = line.path("productId")
            val quantity = line.path("quantity")
            if (!productId.canConvertToLong() || !quantity.canConvertToInt()) {
                throw malformed(envelope, "주문 라인의 productId/quantity 가 숫자가 아니다")
            }
            OrderLine(productId = productId.asLong(), quantity = quantity.asInt())
        }
    }

    private fun malformed(envelope: EventEnvelope, reason: String): MalformedEventException =
        MalformedEventException(
            "$reason: eventId=${envelope.eventId} eventType=${envelope.eventType} aggregateId=${envelope.aggregateId}",
        )

    private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

    private data class OrderLine(val productId: Long, val quantity: Int)
}
