package com.loopers.infrastructure.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.kafka.EventEnvelope
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Transactional Outbox 릴레이 — 미발행(PENDING) 아웃박스를 Kafka 로 발행한다.
 * 브로커 ack(`.get()`) 이후에만 PUBLISHED 로 전이하고, 실패는 PENDING 으로 남겨 다음 주기에 재시도한다(At Least Once).
 * 실패마다 지수 백오프(`nextRetryAt`)를 걸어 poison 이벤트가 매 주기를 잠식하지 않게 하고,
 * 재시도 상한을 소진하면 FAILED 로 격리한다(발행측 DLQ) — 그 애그리거트의 뒤 이벤트는 다음 주기부터 흐른다.
 * 재구동은 FAILED → PENDING 수동 전환(운영 개입)으로 한다.
 * `aggregateId` 를 파티션 key 로 써 같은 애그리거트 이벤트의 순서를 파티션 단위로 보장한다.
 * 같은 애그리거트의 앞선 이벤트가 실패하면 그 애그리거트의 뒤 이벤트는 이번 주기에 발행하지 않는다 —
 * 뒤 이벤트만 먼저 나가면 파티션 안에서 순서가 뒤집히기 때문이다(예: 같은 쿠폰의 선착순 요청 역전).
 * 한 주기는 배치 상한(BATCH_SIZE)·발행 대기 상한(SEND_TIMEOUT)으로 묶어 트랜잭션이 브로커 지연에 무한정 늘어나지 않게 한다.
 * 주기 구동은 [OutboxRelayScheduler] 가 담당한다 — 통합 테스트는 이 함수를 직접 호출해 발행을 명시 제어한다.
 */
@Component
class OutboxRelay(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val kafkaTemplate: KafkaTemplate<Any, Any>,
    private val objectMapper: ObjectMapper,
    meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(OutboxRelay::class.java)

    // FAILED 격리는 운영자가 봐야 하는 사건 — 알림 룰(rate > 0)의 근거 지표.
    private val failedCounter = meterRegistry.counter(METRIC_FAILED)

    @Transactional
    fun relay() {
        val pending = outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING, Pageable.ofSize(BATCH_SIZE))
        val blockedAggregates = mutableSetOf<Pair<String, String>>()
        pending.forEach { event ->
            val aggregateKey = event.aggregateType to event.aggregateId
            if (aggregateKey in blockedAggregates) {
                return@forEach // 같은 애그리거트의 앞선 이벤트가 실패/백오프 대기 — 순서 보존을 위해 이번 주기는 건너뛴다
            }
            if (event.isAwaitingRetry(LocalDateTime.now())) {
                blockedAggregates += aggregateKey // 백오프 대기 — 시도하지 않아야 poison 이 relay 주기를 잠식하지 않는다
                return@forEach
            }
            // 발행 준비(라우팅·봉투 직렬화) 실패는 몇 번을 재시도해도 같은 결과 — 백오프 없이 즉시 격리한다.
            val (topic, envelope) = runCatching { topicOf(event.aggregateType) to envelopeOf(event) }
                .getOrElse { e ->
                    blockedAggregates += aggregateKey
                    event.failPermanently(e.toString())
                    failedCounter.increment()
                    log.error("outbox event is not publishable, moved to FAILED: eventId={}", event.eventId, e)
                    return@forEach
                }
            runCatching {
                kafkaTemplate.send(topic, event.aggregateId, envelope).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }.onSuccess {
                event.markPublished(LocalDateTime.now())
            }.onFailure { e ->
                blockedAggregates += aggregateKey
                event.recordFailure(LocalDateTime.now(), e.toString())
                if (event.status == OutboxStatus.FAILED) {
                    failedCounter.increment()
                    log.error("outbox relay retries exhausted, moved to FAILED: eventId={}", event.eventId, e)
                } else {
                    log.warn(
                        "outbox relay publish failed, keep PENDING for retry: eventId={} retry={}",
                        event.eventId,
                        event.retryCount,
                        e,
                    )
                }
            }
        }
    }

    private fun envelopeOf(event: OutboxEventEntity): EventEnvelope = EventEnvelope(
        eventId = event.eventId,
        eventType = event.eventType,
        aggregateType = event.aggregateType,
        aggregateId = event.aggregateId,
        occurredAt = event.occurredAt,
        payload = objectMapper.readTree(event.payload),
    )

    private fun topicOf(aggregateType: String): String = when (aggregateType) {
        "ORDER" -> ORDER_EVENTS
        "PRODUCT" -> CATALOG_EVENTS
        "COUPON_ISSUE_REQUEST" -> COUPON_ISSUE_REQUESTS
        else -> error("unknown aggregateType for outbox routing: $aggregateType")
    }

    companion object {
        const val ORDER_EVENTS = "order-events"
        const val CATALOG_EVENTS = "catalog-events"
        const val COUPON_ISSUE_REQUESTS = "coupon-issue-requests"
        const val METRIC_FAILED = "outbox.relay.failed"

        private const val BATCH_SIZE = 500
        private const val SEND_TIMEOUT_SECONDS = 10L
    }
}
