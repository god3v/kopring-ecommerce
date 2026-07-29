package com.loopers.interfaces.consumer

import com.loopers.config.kafka.KafkaConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

/**
 * catalog-events·order-events 를 **랭킹 전용 consumer group** 으로 함께 소비한다.
 * 두 토픽 모두 "랭킹판에 반영"이라는 같은 의도로 처리하므로 한 리스너가 구독한다 — eventType 분기는 핸들러가 한다.
 * 상품 지표 집계(기본 그룹)와 오프셋이 독립이라 서로의 실패·지연·재처리에 영향받지 않는다.
 * 배치 처리 후 수동 ack, 레코드 실패는 BatchListenerFailedException 으로 지목해 그 건만 재시도/DLT 격리한다.
 */
@Component
class RankingEventConsumer(
    private val rankingEventHandler: RankingEventHandler,
) {
    @KafkaListener(
        topics = ["\${loopers.kafka.topic.catalog-events}", "\${loopers.kafka.topic.order-events}"],
        groupId = "\${loopers.kafka.consumer.ranking-group-id:loopers-ranking-consumer}",
        containerFactory = KafkaConfig.BATCH_LISTENER,
    )
    fun consume(
        records: List<ConsumerRecord<String, ByteArray>>,
        acknowledgment: Acknowledgment,
    ) {
        records.forEach { record ->
            runCatching { rankingEventHandler.handle(record.value()) }
                .getOrElse { e -> throw BatchListenerFailedException("랭킹 이벤트 반영 실패", e, record) }
        }
        acknowledgment.acknowledge()
    }
}
