package com.loopers.batch.job.productrank

import com.loopers.batch.listener.ChunkListener
import com.loopers.batch.listener.StepMonitorListener
import com.loopers.domain.metrics.ProductPeriodMetrics
import com.loopers.domain.metrics.ProductSignalSummary
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JdbcBatchItemWriter
import org.springframework.batch.item.database.JdbcPagingItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

/**
 * 주간 집계 스텝 2종 — 정리(해당 주 기간 키 delete) 후, 시간별 집계를 주 창으로 합산해 기간 집계 테이블에 적재한다.
 * 정리를 별도 스텝으로 두는 이유: 청크 스텝 재시작 시 정리가 다시 실행돼 이미 적재한 행을 지우는 사고를 막는다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankJob.NAME)
@Configuration
class ProductRankWeeklyStepConfig(
    jobRepository: JobRepository,
    transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
    @Value("\${loopers.batch.product-rank.chunk-size:1000}") chunkSize: Int,
    weights: RankingWeightProperties,
    stepMonitorListener: StepMonitorListener,
    chunkListener: ChunkListener,
) {
    companion object {
        const val WEEKLY_CLEAN_STEP = "weeklyCleanStep"
        const val WEEKLY_AGGREGATE_STEP = "weeklyAggregateStep"
        const val WEEKLY_RANK_STEP = "weeklyRankStep"
        private const val WEEKLY_HOURLY_READER = "weeklyHourlyReader"
    }

    private val steps = ProductRankStepFactory(
        jobRepository = jobRepository,
        transactionManager = transactionManager,
        dataSource = dataSource,
        chunkSize = chunkSize,
        table = "product_metrics_weekly",
        mvTable = "mv_product_rank_weekly",
        periodResolver = RankingPeriod::weeklyOf,
        weights = weights,
        stepMonitorListener = stepMonitorListener,
        chunkListener = chunkListener,
    )

    @Bean(WEEKLY_CLEAN_STEP)
    fun weeklyCleanStep(): Step = steps.taskletStep(WEEKLY_CLEAN_STEP, weeklyCleanTasklet(null))

    @Bean
    @StepScope
    fun weeklyCleanTasklet(@Value("#{jobParameters['targetDate']}") targetDate: String?): Tasklet = steps.cleanTasklet(targetDate)

    @Bean(WEEKLY_AGGREGATE_STEP)
    fun weeklyAggregateStep(): Step = steps.aggregateStep(
        name = WEEKLY_AGGREGATE_STEP,
        reader = weeklyHourlyReader(null),
        processor = weeklyPeriodProcessor(null),
        writer = weeklyMetricsWriter(),
    )

    @Bean(WEEKLY_HOURLY_READER)
    @StepScope
    fun weeklyHourlyReader(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): JdbcPagingItemReader<ProductSignalSummary> = steps.hourlyReader(WEEKLY_HOURLY_READER, targetDate)

    @Bean
    @StepScope
    fun weeklyPeriodProcessor(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): ItemProcessor<ProductSignalSummary, ProductPeriodMetrics> = steps.periodProcessor(targetDate)

    @Bean
    fun weeklyMetricsWriter(): JdbcBatchItemWriter<ProductPeriodMetrics> = steps.metricsWriter()

    @Bean(WEEKLY_RANK_STEP)
    fun weeklyRankStep(): Step = steps.taskletStep(WEEKLY_RANK_STEP, weeklyRankTasklet(null))

    @Bean
    @StepScope
    fun weeklyRankTasklet(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Tasklet = steps.rankTasklet(targetDate)
}
