package com.loopers.application.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.application.queue.result.QueuePositionResult
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.queue.PollingIntervalPolicy
import com.loopers.domain.queue.QueueErrorType
import com.loopers.domain.queue.WaitTimeEstimator
import com.loopers.support.error.CoreException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class QueueFacade(
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenStore: EntryTokenStore,
    @Value("\${loopers.queue.token-ttl-seconds:300}")
    private val tokenTtlSeconds: Long,
    @Value("\${loopers.queue.throughput-per-second:80}")
    private val throughputPerSecond: Double,
) {
    /** 대기열에 진입하고 현재 순번을 반환한다. 이미 진입한 유저는 기존 순번을 유지한다. */
    fun enter(userId: Long): QueuePositionResult {
        waitingQueueRepository.enter(userId, Instant.now())
        return position(userId)
    }

    /**
     * 현재 순번·전체 인원·예상 대기 시간을 조회한다.
     * 입장 토큰이 발급됐으면 함께 반환한다(대기열에서 빠져 position 은 null 이고 entryToken 이 채워진다).
     */
    fun position(userId: Long): QueuePositionResult {
        val rank = waitingQueueRepository.rank(userId)
        val token = entryTokenStore.find(userId)
        return QueuePositionResult(
            position = rank,
            totalWaiting = waitingQueueRepository.size(),
            estimatedWaitSeconds = rank?.let { WaitTimeEstimator.estimateSeconds(it, throughputPerSecond) } ?: 0L,
            entryToken = token?.value,
            pollIntervalSeconds = PollingIntervalPolicy.intervalSeconds(rank),
        )
    }

    /** 대기열 앞에서 batchSize 명을 꺼내 입장 토큰을 발급한다. 발급한 인원 수를 반환한다. */
    fun admit(batchSize: Int): Int {
        val admitted = waitingQueueRepository.pollNext(batchSize.toLong())
        val ttl = Duration.ofSeconds(tokenTtlSeconds)
        admitted.forEach { userId ->
            entryTokenStore.issue(userId, EntryToken.issue(), ttl)
        }
        return admitted.size
    }

    /** 주문 API 진입 검증 — userId 의 토큰이 없거나 값이 다르면 거절한다. */
    fun ensureAdmitted(userId: Long, token: String) {
        val stored = entryTokenStore.find(userId)
        if (stored == null || stored.value != token) {
            throw CoreException(QueueErrorType.ENTRY_TOKEN_INVALID)
        }
    }

    /** 주문 완료 후 입장 토큰을 회수한다. */
    fun leave(userId: Long) {
        entryTokenStore.remove(userId)
    }
}
