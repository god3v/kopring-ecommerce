package com.loopers.batch.job.productrank

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
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
import java.time.LocalDateTime
import java.util.UUID

@SpringBatchTest
@SpringBootTest(
    properties = [
        "spring.batch.job.name=productRankJob",
        "spring.batch.job.enabled=false",
        "loopers.batch.product-rank.chunk-size=2",
    ],
)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class ProductRankMonthlyStepIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @TestConfiguration
    class MonthlyStepTestJobConfig {
        // 실제 productRankJob 빈과 공존하므로 JobLauncherTestUtils 가 이 잡을 집도록 @Primary 를 준다.
        @Primary
        @Bean
        fun monthlyStepTestJob(
            jobRepository: JobRepository,
            @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_CLEAN_STEP) cleanStep: Step,
            @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_AGGREGATE_STEP) aggregateStep: Step,
        ): Job = JobBuilder("monthlyStepTestJob", jobRepository)
            .start(cleanStep)
            .next(aggregateStep)
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

    private fun seedHourly(productId: Long, statHour: LocalDateTime, view: Long = 0, like: Long = 0, order: Long = 0) {
        jdbcTemplate.update(
            "INSERT INTO product_metrics_hourly (product_id, stat_hour, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))",
            productId,
            statHour,
            view,
            like,
            order,
        )
    }

    private fun monthlyRows(): List<Map<String, Any>> =
        jdbcTemplate.queryForList("SELECT product_id, period_key, view_count, like_count, order_quantity FROM product_metrics_monthly ORDER BY product_id")

    private fun Map<String, Any>.long(column: String): Long = (getValue(column) as Number).toLong()

    @DisplayName("월간 집계 스텝을 실행하면,")
    @Nested
    inner class Aggregate {
        @Test
        fun `월 창 안의 시간별 신호를 상품별로 합산하고 창 밖은 섞지 않는다`() {
            // targetDate=2026-07-21 → 월 창 [2026-07-01 00:00, 2026-08-01 00:00)
            seedHourly(101L, LocalDateTime.of(2026, 7, 1, 0, 0), view = 1)
            seedHourly(101L, LocalDateTime.of(2026, 7, 31, 23, 0), view = 2, like = -1, order = 4)
            // 창 밖 — 전월 마지막 시각, 다음 달 1일 0시(끝 경계 제외)
            seedHourly(101L, LocalDateTime.of(2026, 6, 30, 23, 0), view = 100)
            seedHourly(101L, LocalDateTime.of(2026, 8, 1, 0, 0), view = 100)
            seedHourly(202L, LocalDateTime.of(2026, 7, 15, 12, 0), view = 1)

            val status = launch()

            assertThat(status).isEqualTo(BatchStatus.COMPLETED)
            val rows = monthlyRows()
            assertThat(rows).hasSize(2)
            assertThat(rows.map { it["period_key"] }).containsOnly("202607")
            with(rows.single { it.long("product_id") == 101L }) {
                assertThat(long("view_count")).isEqualTo(3L)
                assertThat(long("like_count")).isEqualTo(-1L)
                assertThat(long("order_quantity")).isEqualTo(4L)
            }
        }

        @Test
        fun `같은 targetDate 로 다시 실행해도 결과가 같다`() {
            seedHourly(101L, LocalDateTime.of(2026, 7, 21, 10, 0), view = 3, like = 1, order = 2)

            launch()
            val second = launch()

            assertThat(second).isEqualTo(BatchStatus.COMPLETED)
            val rows = monthlyRows()
            assertThat(rows).hasSize(1)
            with(rows.single()) {
                assertThat(long("view_count")).isEqualTo(3L)
                assertThat(long("like_count")).isEqualTo(1L)
                assertThat(long("order_quantity")).isEqualTo(2L)
            }
        }
    }
}
