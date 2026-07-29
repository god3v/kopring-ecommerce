package com.loopers.infrastructure.outbox

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

/**
 * Transactional Outbox 레코드 — 순수 인프라 기술 구현. 도메인 소비자가 없어 별도 POJO/포트를 두지 않고 엔티티로 둔다.
 * 도메인 변경과 같은 트랜잭션에서 브리지가 적재하고, 릴레이가 Kafka 로 발행한 뒤 상태를 전이한다.
 * `payload` 는 발행 이벤트를 직렬화한 봉투이며, `eventId` 는 Consumer 멱등(`event_handled`) 키로 전달된다.
 */
@Entity
@Table(name = "outbox")
class OutboxEventEntity private constructor(
    eventId: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    payload: String,
    occurredAt: LocalDateTime,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    var eventId: String = eventId
        protected set

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    var aggregateType: String = aggregateType
        protected set

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    var aggregateId: String = aggregateId
        protected set

    @Column(name = "event_type", nullable = false, updatable = false)
    var eventType: String = eventType
        protected set

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    var payload: String = payload
        protected set

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: LocalDateTime = occurredAt
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OutboxStatus = OutboxStatus.PENDING
        protected set

    @Column(name = "published_at")
    var publishedAt: LocalDateTime? = null
        protected set

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0
        protected set

    @Column(name = "next_retry_at")
    var nextRetryAt: LocalDateTime? = null
        protected set

    @Column(name = "last_error", length = LAST_ERROR_MAX_LENGTH)
    var lastError: String? = null
        protected set

    fun markPublished(at: LocalDateTime) {
        status = OutboxStatus.PUBLISHED
        publishedAt = at
    }

    /**
     * 발행 실패를 기록한다 — 재시도 횟수를 늘리고 지수 백오프를 건다.
     * 상한을 소진하면 FAILED 로 격리해 폴링 대상에서 제외한다(발행측 DLQ). 재구동은 FAILED → PENDING 수동 전환으로 한다.
     */
    fun recordFailure(at: LocalDateTime, cause: String) {
        retryCount += 1
        lastError = cause.take(LAST_ERROR_MAX_LENGTH)
        nextRetryAt = at.plusSeconds(backoffSeconds())
        if (retryCount >= MAX_RETRY_COUNT) {
            status = OutboxStatus.FAILED
        }
    }

    /** 백오프 시각이 아직 오지 않아 이번 주기에 재시도하면 안 되는 상태인지 */
    fun isAwaitingRetry(at: LocalDateTime): Boolean = nextRetryAt?.isAfter(at) == true

    /** 비재시도성 실패(라우팅 불가·payload 손상 등) — 재시도가 무의미하므로 즉시 FAILED 로 격리한다. */
    fun failPermanently(cause: String) {
        lastError = cause.take(LAST_ERROR_MAX_LENGTH)
        status = OutboxStatus.FAILED
    }

    private fun backoffSeconds(): Long =
        (INITIAL_BACKOFF_SECONDS shl (retryCount - 1)).coerceAtMost(MAX_BACKOFF_SECONDS)

    companion object {
        private const val MAX_RETRY_COUNT = 10
        private const val INITIAL_BACKOFF_SECONDS = 1L
        private const val MAX_BACKOFF_SECONDS = 300L
        private const val LAST_ERROR_MAX_LENGTH = 500

        fun create(
            eventId: UUID,
            aggregateType: String,
            aggregateId: String,
            eventType: String,
            payload: String,
            occurredAt: LocalDateTime,
        ): OutboxEventEntity = OutboxEventEntity(
            eventId = eventId.toString(),
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            occurredAt = occurredAt,
        )
    }
}
