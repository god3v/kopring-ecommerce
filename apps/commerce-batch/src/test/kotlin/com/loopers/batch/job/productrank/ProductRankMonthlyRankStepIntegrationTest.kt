package com.loopers.batch.job.productrank

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

@SpringBatchTest
@SpringBootTest(
    properties = [
        "spring.batch.job.name=productRankJob",
        "spring.batch.job.enabled=false",
    ],
)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class ProductRankMonthlyRankStepIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @TestConfiguration
    class MonthlyRankStepTestJobConfig {
        // 실제 productRankJob 빈과 공존하므로 JobLauncherTestUtils 가 이 잡을 집도록 @Primary 를 준다.
        @Primary
        @Bean
        fun monthlyRankStepTestJob(
            jobRepository: JobRepository,
            @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_RANK_STEP) rankStep: Step,
        ): Job = JobBuilder("monthlyRankStepTestJob", jobRepository)
            .start(rankStep)
            .build()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    // JobExecution 을 반환하면 @SpringBatchTest 의 스코프 리스너가 팩토리 메서드로 오인해 무인자 호출을 시도한다 — 상태만 반환한다.
    private fun launch(targetDate: String = "2026-07-21"): BatchStatus = jobLauncherTestUtils.launchJob(params(targetDate)).status

    private fun params(targetDate: String): JobParameters = JobParametersBuilder()
        .addString("targetDate", targetDate)
        .addString("uniqueRunId", UUID.randomUUID().toString())
        .toJobParameters()

    private fun seedMonthly(productId: Long, periodKey: String = "202607", view: Long = 0, like: Long = 0, order: Long = 0) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_monthly (product_id, period_key, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))",
            productId,
            periodKey,
            view,
            like,
            order,
        )
    }

    private fun mvRows(): List<Map<String, Any>> =
        jdbcTemplate.queryForList("SELECT period_key, rank_no, product_id, score FROM mv_product_rank_monthly ORDER BY rank_no")

    private fun Map<String, Any>.long(column: String): Long = (getValue(column) as Number).toLong()

    private fun Map<String, Any>.double(column: String): Double = (getValue(column) as Number).toDouble()

    @DisplayName("월간 랭킹 스텝을 실행하면,")
    @Nested
    inner class Rank {
        @Test
        fun `가중치를 적용한 점수 내림차순으로 순위를 매기고 다른 기간은 섞지 않는다`() {
            seedMonthly(101L, order = 1)
            seedMonthly(202L, like = 3)
            seedMonthly(303L, periodKey = "202606", view = 999)

            val status = launch()

            assertThat(status).isEqualTo(BatchStatus.COMPLETED)
            val rows = mvRows()
            assertThat(rows).hasSize(2)
            assertThat(rows.map { it["period_key"] }).containsOnly("202607")
            assertThat(rows.map { it.long("product_id") }).containsExactly(101L, 202L)
            assertThat(rows[0].double("score")).isCloseTo(0.7, within(1e-9))
            assertThat(rows[1].double("score")).isCloseTo(0.6, within(1e-9))
        }

        @Test
        fun `같은 기간 키로 다시 실행해도 결과가 같다`() {
            seedMonthly(101L, view = 5)

            launch()
            val second = launch()

            assertThat(second).isEqualTo(BatchStatus.COMPLETED)
            val rows = mvRows()
            assertThat(rows).hasSize(1)
            assertThat(rows.single().long("rank_no")).isEqualTo(1L)
        }
    }
}
