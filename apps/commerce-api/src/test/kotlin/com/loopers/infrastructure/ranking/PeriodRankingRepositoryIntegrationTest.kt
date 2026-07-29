package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.PeriodRankingRepository
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class PeriodRankingRepositoryIntegrationTest @Autowired constructor(
    private val periodRankingRepository: PeriodRankingRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun seed(table: String, periodKey: String, rankNo: Int, productId: Long, score: Double) {
        jdbcTemplate.update(
            "INSERT INTO $table (period_key, rank_no, product_id, score, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            periodKey,
            rankNo,
            productId,
            score,
        )
    }

    private fun seedWeekly(periodKey: String, rankNo: Int, productId: Long, score: Double) =
        seed("mv_product_rank_weekly", periodKey, rankNo, productId, score)

    private fun seedMonthly(periodKey: String, rankNo: Int, productId: Long, score: Double) =
        seed("mv_product_rank_monthly", periodKey, rankNo, productId, score)

    @DisplayName("Top-N 조회는,")
    @Nested
    inner class TopN {
        @Test
        fun `순위 순으로 페이지를 슬라이스해 상품과 점수를 반환하고 다른 기간 키는 섞지 않는다`() {
            seedWeekly("2026W30", 1, 101L, 3.0)
            seedWeekly("2026W30", 2, 202L, 2.0)
            seedWeekly("2026W30", 3, 303L, 1.0)
            seedWeekly("2026W29", 1, 999L, 9.9)

            val firstPage = periodRankingRepository.topN(RankingPeriod.WEEKLY, "2026W30", page = 0, size = 2)
            val secondPage = periodRankingRepository.topN(RankingPeriod.WEEKLY, "2026W30", page = 1, size = 2)

            assertThat(firstPage.map { it.productId }).containsExactly(101L, 202L)
            assertThat(firstPage.map { it.score }).containsExactly(3.0, 2.0)
            assertThat(secondPage.map { it.productId }).containsExactly(303L)
        }

        @Test
        fun `월간은 월간 테이블에서 읽는다`() {
            seedWeekly("2026W30", 1, 101L, 3.0)
            seedMonthly("202607", 1, 404L, 5.0)

            val entries = periodRankingRepository.topN(RankingPeriod.MONTHLY, "202607", page = 0, size = 10)

            assertThat(entries.map { it.productId }).containsExactly(404L)
        }
    }

    @DisplayName("개수 조회는,")
    @Nested
    inner class Size {
        @Test
        fun `기간 키의 총 개수를 반환하고 없으면 0 이다`() {
            seedWeekly("2026W30", 1, 101L, 3.0)
            seedWeekly("2026W30", 2, 202L, 2.0)
            seedMonthly("202607", 1, 404L, 5.0)

            assertThat(periodRankingRepository.size(RankingPeriod.WEEKLY, "2026W30")).isEqualTo(2L)
            assertThat(periodRankingRepository.size(RankingPeriod.MONTHLY, "202607")).isEqualTo(1L)
            assertThat(periodRankingRepository.size(RankingPeriod.WEEKLY, "2026W01")).isEqualTo(0L)
        }
    }
}
