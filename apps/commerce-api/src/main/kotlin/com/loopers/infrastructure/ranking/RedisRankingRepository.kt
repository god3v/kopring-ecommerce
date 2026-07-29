package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

/**
 * 랭킹판 Redis 읽기 어댑터.
 * ZSET 조회는 master 템플릿으로 한다 — 대기열과 같은 이유로, 갱신 직후에도 순위가 흔들리지 않게 최신 상태를 읽는다.
 */
@Component
class RedisRankingRepository(
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val redisTemplate: RedisTemplate<String, String>,
) : RankingRepository {
    override fun topN(key: String, offset: Long, size: Long): List<RankedEntry> {
        if (size <= 0) return emptyList()
        val tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, offset, offset + size - 1).orEmpty()
        return tuples.mapNotNull { tuple ->
            val productId = tuple.value?.toLongOrNull() ?: return@mapNotNull null
            val score = tuple.score ?: return@mapNotNull null
            RankedEntry(productId, score)
        }
    }

    override fun rankOf(key: String, productId: Long): Long? =
        // ZREVRANK 는 0-based — 사용자에게 노출하는 순위는 1부터라 +1 한다.
        redisTemplate.opsForZSet().reverseRank(key, productId.toString())?.plus(1)

    override fun size(key: String): Long =
        redisTemplate.opsForZSet().zCard(key) ?: 0L
}
