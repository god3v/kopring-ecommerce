package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.coupon.FirstComeIssueFacade
import com.loopers.config.kafka.KafkaConfig
import com.loopers.kafka.EventEnvelope
import com.loopers.kafka.MalformedEventException
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * coupon-issue-requests 소비자 — 접수된 선착순 발급 요청을 처리한다.
 * 요청 식별자만 꺼내 처리기로 넘기고(발급 근거는 접수 레코드), 배치 처리 완료 후 수동 ack 한다.
 * 이미 확정된 요청은 처리기가 멱등하게 건너뛴다.
 * 레코드 처리 실패는 BatchListenerFailedException 으로 실패 지점을 지목한다 —
 * 형식이 깨진 메시지(역직렬화 불가·requestId 누락)는 MalformedEventException 이라 재시도 없이 DLT 로 격리되고,
 * 처리(DB) 실패는 재시도 후 DLT 로 간다. 어느 쪽도 배치 전체를 다시 돌리거나 파티션을 막지 않는다.
 */
@Component
class CouponIssueRequestConsumer(
    private val firstComeIssueFacade: FirstComeIssueFacade,
    private val objectMapper: ObjectMapper,
) {
    @KafkaListener(
        topics = ["\${loopers.kafka.topic.coupon-issue-requests}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            runCatching { firstComeIssueFacade.handle(requestIdOf(record.value())) }
                .getOrElse { e -> throw BatchListenerFailedException("쿠폰 발급 요청 처리 실패", e, record) }
        }
        acknowledgment.acknowledge()
    }

    private fun requestIdOf(message: ByteArray): String {
        val envelope = runCatching { objectMapper.readValue(message, EventEnvelope::class.java) }
            .getOrElse { e -> throw MalformedEventException("역직렬화할 수 없는 메시지", e) }
        val requestId = envelope.payload.path("requestId").asText("")
        if (requestId.isBlank()) {
            throw MalformedEventException("requestId 없는 발급 요청 이벤트: eventId=${envelope.eventId}")
        }
        return requestId
    }
}
