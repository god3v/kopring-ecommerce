package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingRepository
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate

@SpringBootTest
@Import(RedisTestContainersConfig::class)
class RedisRankingRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val key = "rank:all:20260714"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun seed(productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(key, productId.toString(), score)
    }

    @DisplayName("Top-N 조회는,")
    @Nested
    inner class TopN {
        @Test
        fun `점수 내림차순으로 상품 식별자와 점수를 반환한다`() {
            seed(productId = 101L, score = 1.0)
            seed(productId = 202L, score = 3.0)
            seed(productId = 303L, score = 2.0)

            val result = rankingRepository.topN(key, offset = 0, size = 10)

            assertThat(result).containsExactly(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
                RankedEntry(productId = 101L, score = 1.0),
            )
        }

        @Test
        fun `페이지 구간에 해당하는 항목만 반환한다`() {
            seed(productId = 202L, score = 5.0)
            seed(productId = 303L, score = 4.0)
            seed(productId = 404L, score = 3.0)
            seed(productId = 505L, score = 2.0)
            seed(productId = 101L, score = 1.0)

            val result = rankingRepository.topN(key, offset = 2, size = 2)

            assertThat(result).containsExactly(
                RankedEntry(productId = 404L, score = 3.0),
                RankedEntry(productId = 505L, score = 2.0),
            )
        }

        @Test
        fun `존재하지 않는 날짜의 랭킹판 조회는 빈 목록을 반환한다`() {
            val result = rankingRepository.topN("rank:all:19990101", offset = 0, size = 10)

            assertThat(result).isEmpty()
        }
    }

    @DisplayName("순위를 조회하면,")
    @Nested
    inner class RankOf {
        @Test
        fun `가장 점수 높은 상품의 순위는 1이다`() {
            seed(productId = 202L, score = 3.0)
            seed(productId = 303L, score = 2.0)
            seed(productId = 101L, score = 1.0)

            assertThat(rankingRepository.rankOf(key, productId = 202L)).isEqualTo(1L)
        }

        @Test
        fun `랭킹판에 없는 상품의 순위 조회는 null 을 반환한다`() {
            seed(productId = 202L, score = 3.0)

            assertThat(rankingRepository.rankOf(key, productId = 999L)).isNull()
        }
    }

    @DisplayName("전체 항목 수를 조회하면,")
    @Nested
    inner class Size {
        @Test
        fun `랭킹판에 담긴 상품 수를 반환한다`() {
            seed(productId = 202L, score = 3.0)
            seed(productId = 303L, score = 2.0)
            seed(productId = 101L, score = 1.0)

            assertThat(rankingRepository.size(key)).isEqualTo(3L)
        }
    }
}
