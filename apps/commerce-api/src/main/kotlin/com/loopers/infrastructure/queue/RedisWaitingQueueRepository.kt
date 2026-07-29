package com.loopers.infrastructure.queue

import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.config.redis.RedisConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * 대기열 Redis 어댑터 — Sorted Set(score=진입 시각, member=userId).
 * 진입은 addIfAbsent(ZADD NX) 로 재진입 시 기존 순서를 보존한다.
 * 순번 정확성이 중요하므로 조회도 master 템플릿을 써서 replica 지연을 배제한다.
 */
@Component
class RedisWaitingQueueRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : WaitingQueueRepository {
    override fun enter(userId: Long, at: Instant): Boolean =
        redisTemplate.opsForZSet()
            .addIfAbsent(QUEUE_KEY, userId.toString(), at.toEpochMilli().toDouble()) ?: false

    override fun rank(userId: Long): Long? =
        redisTemplate.opsForZSet().rank(QUEUE_KEY, userId.toString())

    override fun size(): Long =
        redisTemplate.opsForZSet().zCard(QUEUE_KEY) ?: 0L

    override fun pollNext(count: Long): List<Long> {
        if (count <= 0) return emptyList()
        val popped = redisTemplate.opsForZSet().popMin(QUEUE_KEY, count) ?: return emptyList()
        return popped.mapNotNull { it.value?.toLongOrNull() }
    }

    companion object {
        private const val QUEUE_KEY = "queue:waiting:v1"
    }
}
