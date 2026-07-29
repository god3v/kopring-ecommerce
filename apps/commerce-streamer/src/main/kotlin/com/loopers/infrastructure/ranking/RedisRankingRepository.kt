package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.connection.zset.Aggregate
import org.springframework.data.redis.connection.zset.Weights
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 랭킹판 Redis 어댑터. 점수 누적은 master 에 즉시 반영한다.
 *
 * 멱등 표식(SETNX)과 점수 증분(ZINCRBY)을 하나의 Lua 로 원자 실행한다 — 처음 보는 이벤트일 때만 증분한다.
 * 두 연산이 쪼개지지 않으므로 재전달에도 표식만 남거나 증분만 되는 어긋남이 없다(유실·중복 없음).
 */
@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun incrementScoreOnce(eventId: UUID, key: String, productId: Long, delta: Double, ttlSeconds: Long) {
        masterTemplate.execute(
            INCREMENT_ONCE,
            listOf(seenKey(eventId, productId), key),
            delta.toString(),
            productId.toString(),
            ttlSeconds.toString(),
        )
    }

    override fun removeProduct(keys: List<String>, productId: Long) {
        val member = productId.toString()
        keys.forEach { masterTemplate.opsForZSet().remove(it, member) }
    }

    override fun carryOver(sourceKey: String, destKey: String, weight: Double, ttlSeconds: Long) {
        // 이미 오늘 점수가 쌓였다면 덮어쓰지 않는다(중복 실행·자정 후 오발동 방어).
        if (masterTemplate.hasKey(destKey)) return
        // ZUNIONSTORE dest 1 source WEIGHTS weight — source 가 없으면 결과가 비어 dest 를 만들지 않는다.
        masterTemplate.opsForZSet().unionAndStore(sourceKey, emptyList(), destKey, Aggregate.SUM, Weights.of(weight))
        masterTemplate.expire(destKey, Duration.ofSeconds(ttlSeconds))
    }

    override fun rebuild(key: String, entries: List<RankedEntry>, ttlSeconds: Long) {
        if (entries.isEmpty()) return
        val stagingKey = "$key:rebuild"
        // 지난 재구축이 교체 직전에 죽었어도 잔재 위에 쌓지 않는다.
        masterTemplate.delete(stagingKey)
        val tuples = entries.map { ZSetOperations.TypedTuple.of(it.productId.toString(), it.score) }.toSet()
        masterTemplate.opsForZSet().add(stagingKey, tuples)
        masterTemplate.expire(stagingKey, Duration.ofSeconds(ttlSeconds))
        // RENAME 은 원자 교체 — 읽는 쪽은 교체 전의 판 아니면 완성된 판만 본다.
        masterTemplate.rename(stagingKey, key)
    }

    override fun exists(key: String): Boolean = masterTemplate.hasKey(key)

    // 멱등 단위는 (이벤트, 상품) — 한 주문(같은 eventId)의 여러 상품 라인이 서로를 막지 않게 한다.
    private fun seenKey(eventId: UUID, productId: Long): String = "rank:seen:$eventId:$productId"

    companion object {
        // KEYS[1]=멱등 표식 키, KEYS[2]=랭킹판 키, ARGV[1]=증분 점수, ARGV[2]=상품 식별자(member), ARGV[3]=보존 기간(초)
        private val INCREMENT_ONCE = DefaultRedisScript(
            """
            if redis.call('SETNX', KEYS[1], '1') == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[3])
                redis.call('ZINCRBY', KEYS[2], ARGV[1], ARGV[2])
                redis.call('EXPIRE', KEYS[2], ARGV[3])
                return 1
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}
