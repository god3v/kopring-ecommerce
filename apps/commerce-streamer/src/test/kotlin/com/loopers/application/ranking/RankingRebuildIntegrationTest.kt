package com.loopers.application.ranking

import com.loopers.application.metrics.ProductMetricsFacade
import com.loopers.application.metrics.SalesLine
import com.loopers.config.redis.RedisConfig
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 유실 복구 통합 검증 — 실 DB(시간별 집계)·실 Redis 로, 판이 없는 상태에서 재구축이 집계 기반 점수로 판을 되살리는지 관통한다.
 */
@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@DisplayName("랭킹 재구축 통합")
class RankingRebuildIntegrationTest @Autowired constructor(
    private val productMetricsFacade: ProductMetricsFacade,
    private val rankingFacade: RankingFacade,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)
    private val date = LocalDate.of(2026, 7, 14)
    private val key = "rank:all:20260714"

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `판을 비운 뒤 재구축하면 집계 기반 점수로 판이 되살아난다`() {
        // 신호가 시간별 집계(RDB SoT)에 쌓인다 — 주문 1건(0.7) vs 좋아요 3건(0.6).
        productMetricsFacade.addSales(UUID.randomUUID(), listOf(SalesLine(productId = 101L, quantity = 1)), occurredAt)
        repeat(3) { productMetricsFacade.increaseLike(UUID.randomUUID(), productId = 202L, occurredAt = occurredAt) }
        // 유실 시나리오: 판에는 어중간한 잔재만 남았다 — 재구축이 이것까지 통째로 대체해야 한다.
        masterTemplate.opsForZSet().add(key, "999", 9.9)

        rankingFacade.rebuild(date)

        assertThat(masterTemplate.opsForZSet().reverseRange(key, 0, -1)?.toList()).containsExactly("101", "202")
        assertThat(scoreOf(101L)).isCloseTo(0.7, within(1e-9))
        assertThat(scoreOf(202L)).isCloseTo(0.6, within(1e-9))
    }

    private fun scoreOf(productId: Long): Double? =
        masterTemplate.opsForZSet().score(key, productId.toString())
}
