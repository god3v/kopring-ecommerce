package com.loopers.batch.job.productrank

import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.batch.support.transaction.ResourcelessTransactionManager
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
import java.util.concurrent.atomic.AtomicBoolean

// 재시작 학습 검증 — 실제 주간 스텝 사이에 첫 실행만 실패하는 스텝을 끼워, 같은 파라미터 재실행이
// 완료된 스텝을 건너뛰고 실패 스텝부터 이어가는지 확인한다.
@SpringBatchTest
@SpringBootTest(
    properties = [
        "spring.batch.job.name=productRankJob",
        "spring.batch.job.enabled=false",
    ],
)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class ProductRankJobRestartIntegrationTest @Autowired constructor(
    private val jobLauncherTestUtils: JobLauncherTestUtils,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @TestConfiguration
    class RestartTestJobConfig {
        companion object {
            val FAIL_ONCE = AtomicBoolean(true)
        }

        @Bean
        fun failOnceStep(jobRepository: JobRepository): Step = StepBuilder("failOnceStep", jobRepository)
            .tasklet(
                { _, _ ->
                    if (FAIL_ONCE.getAndSet(false)) error("재시작 검증을 위해 첫 실행은 실패시킨다")
                    RepeatStatus.FINISHED
                },
                ResourcelessTransactionManager(),
            )
            .build()

        // 실제 productRankJob 빈과 공존하므로 JobLauncherTestUtils 가 이 잡을 집도록 @Primary 를 준다.
        @Primary
        @Bean
        fun restartTestJob(
            jobRepository: JobRepository,
            @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_CLEAN_STEP) cleanStep: Step,
            @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_AGGREGATE_STEP) aggregateStep: Step,
            @Qualifier("failOnceStep") failOnceStep: Step,
            @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_RANK_STEP) rankStep: Step,
        ): Job = JobBuilder("restartTestJob", jobRepository)
            .start(cleanStep)
            .next(aggregateStep)
            .next(failOnceStep)
            .next(rankStep)
            .build()
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("실패한 잡을 같은 파라미터로 다시 실행하면 실패 스텝부터 재시작한다")
    @Test
    fun restartFromFailedStep() {
        RestartTestJobConfig.FAIL_ONCE.set(true)
        jdbcTemplate.update(
            "INSERT INTO product_metrics_hourly (product_id, stat_hour, view_count, like_count, order_quantity, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))",
            101L,
            LocalDateTime.of(2026, 7, 21, 10, 0),
            5L,
            0L,
            0L,
        )
        // 재시작은 같은 JobInstance 여야 하므로 두 실행이 완전히 같은 파라미터를 쓴다.
        val params = JobParametersBuilder().addString("targetDate", "2026-07-21").toJobParameters()

        val first = jobLauncherTestUtils.launchJob(params)

        assertThat(first.status).isEqualTo(BatchStatus.FAILED)
        assertThat(first.stepExecutions.map { it.stepName })
            .containsExactly("weeklyCleanStep", "weeklyAggregateStep", "failOnceStep")
        // 실패 전까지의 스텝은 커밋되어 있다.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM product_metrics_weekly", Long::class.java)).isEqualTo(1L)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_weekly", Long::class.java)).isEqualTo(0L)

        val second = jobLauncherTestUtils.launchJob(params)

        assertThat(second.status).isEqualTo(BatchStatus.COMPLETED)
        // 완료된 정리·집계 스텝은 다시 실행되지 않고, 실패 스텝부터 이어간다.
        assertThat(second.stepExecutions.map { it.stepName }).containsExactly("failOnceStep", "weeklyRankStep")
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mv_product_rank_weekly", Long::class.java)).isEqualTo(1L)
    }
}
