package com.loopers.infrastructure.ranking

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import java.util.UUID
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class RedisRankingRepositoryIntegrationTest @Autowired constructor(
    private val rankingRepository: RankingRepository,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
) {
    private val key = "rank:all:20260714"
    private val ttlSeconds = 172_800L

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    private fun scoreOf(productId: Long): Double? = scoreOf(key, productId)

    private fun scoreOf(rankKey: String, productId: Long): Double? =
        masterTemplate.opsForZSet().score(rankKey, productId.toString())

    private fun sizeOf(rankKey: String): Long =
        masterTemplate.opsForZSet().zCard(rankKey) ?: 0L

    private fun seed(rankKey: String, productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(rankKey, productId.toString(), score)
    }

    @DisplayName("점수를 누적하면,")
    @Nested
    inner class IncrementScore {
        @Test
        fun `랭킹판에서 그 상품의 점수가 증가한다`() {
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(0.7)
        }

        @Test
        fun `서로 다른 이벤트로 같은 상품에 누적하면 점수가 합산된다`() {
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.2, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isCloseTo(0.9, within(1e-9))
        }

        @Test
        fun `음수 증분을 누적하면 점수가 감소하고 0 아래로도 내려간다`() {
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.2, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = -0.7, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isCloseTo(-0.5, within(1e-9))
        }

        @Test
        fun `서로 다른 날짜 키에 누적한 점수는 섞이지 않는다`() {
            val otherKey = "rank:all:20260713"
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), otherKey, productId = 101L, delta = 0.2, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(0.7)
            assertThat(scoreOf(otherKey, 101L)).isEqualTo(0.2)
        }
    }

    @DisplayName("같은 eventId 로 다시 누적하면,")
    @Nested
    inner class Idempotency {
        @Test
        fun `점수는 한 번만 반영된다`() {
            val eventId = UUID.randomUUID()

            rankingRepository.incrementScoreOnce(eventId, key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(eventId, key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(0.7)
        }

        @Test
        fun `같은 eventId 라도 상품이 다르면 각각 반영된다`() {
            // 한 주문(같은 eventId)에 여러 상품 라인이 있으면 라인마다 반영돼야 한다.
            val eventId = UUID.randomUUID()

            rankingRepository.incrementScoreOnce(eventId, key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(eventId, key, productId = 202L, delta = 1.4, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(0.7)
            assertThat(scoreOf(202L)).isEqualTo(1.4)
        }
    }

    @DisplayName("상품을 제거하면,")
    @Nested
    inner class RemoveProduct {
        @Test
        fun `지정한 날짜 키들에서 그 상품이 사라진다`() {
            val yesterday = "rank:all:20260713"
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)
            rankingRepository.incrementScoreOnce(UUID.randomUUID(), yesterday, productId = 101L, delta = 0.5, ttlSeconds = ttlSeconds)

            rankingRepository.removeProduct(listOf(key, yesterday), productId = 101L)

            assertThat(scoreOf(101L)).isNull()
            assertThat(scoreOf(yesterday, 101L)).isNull()
        }
    }

    @DisplayName("점수를 누적하면 보존 기간이,")
    @Nested
    inner class Ttl {
        @Test
        fun `랭킹판과 멱등 표식 양쪽에 설정된다`() {
            val eventId = UUID.randomUUID()

            rankingRepository.incrementScoreOnce(eventId, key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)

            assertThat(masterTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(ttlSeconds - 10, ttlSeconds)
            assertThat(masterTemplate.getExpire("rank:seen:$eventId:101", TimeUnit.SECONDS)).isBetween(ttlSeconds - 10, ttlSeconds)
        }
    }

    @DisplayName("재구축하면,")
    @Nested
    inner class Rebuild {
        @Test
        fun `기존 판이 통째로 대체되고 보존 기간이 설정된다`() {
            // 유실 후 어중간하게 남은 잔재가 있어도 재구축 결과만 남아야 한다.
            seed(key, productId = 999L, score = 9.9)

            rankingRepository.rebuild(key, listOf(RankedEntry(101L, 3.0), RankedEntry(202L, 1.0)), ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(3.0)
            assertThat(scoreOf(202L)).isEqualTo(1.0)
            assertThat(scoreOf(999L)).isNull()
            assertThat(masterTemplate.getExpire(key, TimeUnit.SECONDS)).isBetween(ttlSeconds - 10, ttlSeconds)
        }

        @Test
        fun `재구축할 점수가 없으면 기존 판을 건드리지 않는다`() {
            seed(key, productId = 101L, score = 5.0)

            rankingRepository.rebuild(key, emptyList(), ttlSeconds)

            assertThat(scoreOf(101L)).isEqualTo(5.0)
        }
    }

    @DisplayName("이월(carry-over)하면,")
    @Nested
    inner class CarryOver {
        private val yesterday = "rank:all:20260713"

        @Test
        fun `전일 점수에 이월 가중치를 곱해 다음 날 랭킹판으로 복사한다`() {
            seed(yesterday, productId = 101L, score = 10.0)

            rankingRepository.carryOver(yesterday, key, weight = 0.1, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isCloseTo(1.0, within(1e-9))
        }

        @Test
        fun `이월 후 유입되는 점수는 이월 점수 위에 누적된다`() {
            seed(yesterday, productId = 101L, score = 10.0)
            rankingRepository.carryOver(yesterday, key, weight = 0.1, ttlSeconds = ttlSeconds)

            rankingRepository.incrementScoreOnce(UUID.randomUUID(), key, productId = 101L, delta = 0.7, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(101L)).isCloseTo(1.7, within(1e-9))
        }

        @Test
        fun `전일 랭킹판이 없으면 이월은 아무것도 만들지 않는다`() {
            rankingRepository.carryOver(yesterday, key, weight = 0.1, ttlSeconds = ttlSeconds)

            assertThat(sizeOf(key)).isEqualTo(0L)
        }

        @Test
        fun `다음 날 랭킹판이 이미 존재하면 이월은 아무것도 하지 않는다`() {
            seed(yesterday, productId = 101L, score = 10.0)
            seed(key, productId = 202L, score = 5.0)

            rankingRepository.carryOver(yesterday, key, weight = 0.1, ttlSeconds = ttlSeconds)

            assertThat(scoreOf(202L)).isEqualTo(5.0)
            assertThat(scoreOf(101L)).isNull()
        }
    }
}
