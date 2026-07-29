package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * order-events 소비자 — 주문 생성 이벤트의 라인을 상품별 판매량으로 누적한다.
 * catalog-events 와 같은 규칙: 배치 처리 완료 후 수동 ack, 중복은 멱등(eventId)으로 흡수.
 * 레코드 처리 실패는 BatchListenerFailedException 으로 실패 지점을 지목한다 —
 * 에러 핸들러가 앞 레코드는 커밋하고 그 레코드만 재시도/DLT 격리해 배치 전체 재전달을 막는다.
 */
@Component
class OrderEventConsumer(
    private val metricsEventHandler: MetricsEventHandler,
) {
    @KafkaListener(
        topics = ["\${loopers.kafka.topic.order-events}"],
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            runCatching { metricsEventHandler.handle(record.value()) }
                .getOrElse { e -> throw BatchListenerFailedException("주문 이벤트 처리 실패", e, record) }
        }
        acknowledgment.acknowledge()
    }
}
