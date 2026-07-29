package com.loopers.infrastructure.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.queue.EntryToken
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 입장 토큰 Redis 어댑터 — String 키(`entry-token:{userId}`) + TTL.
 * 저장소 장애는 fail-closed 로 흡수한다: find 는 null(→ 관문이 거절), issue/remove 는 no-op.
 * 이렇게 하면 Redis 다운 시 미인가 입장을 막고, 주문 완료 이벤트의 비동기 토큰 회수도 예외를 전파하지 않는다.
 */
@Component
class RedisEntryTokenStore(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : EntryTokenStore {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun issue(userId: Long, token: EntryToken, ttl: Duration) {
        runCatching { redisTemplate.opsForValue().set(key(userId), token.value, ttl) }
            .onFailure { log.warn("입장 토큰 발급 실패 (userId={})", userId, it) }
    }

    override fun find(userId: Long): EntryToken? = runCatching {
        redisTemplate.opsForValue().get(key(userId))?.let { EntryToken(it) }
    }.getOrElse {
        log.warn("입장 토큰 조회 실패 (userId={})", userId, it)
        null
    }

    override fun remove(userId: Long) {
        runCatching { redisTemplate.delete(key(userId)) }
            .onFailure { log.warn("입장 토큰 제거 실패 (userId={})", userId, it) }
    }

    private fun key(userId: Long): String = "$KEY_PREFIX$userId"

    companion object {
        private const val KEY_PREFIX = "entry-token:"
    }
}
