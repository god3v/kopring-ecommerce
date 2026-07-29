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
 * 월간 집계 스텝 2종 — 주간과 같은 구성에서 대상 테이블과 기간 계산(달력 월)만 다르다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankJob.NAME)
@Configuration
class ProductRankMonthlyStepConfig(
    jobRepository: JobRepository,
    transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
    @Value("\${loopers.batch.product-rank.chunk-size:1000}") chunkSize: Int,
    weights: RankingWeightProperties,
    stepMonitorListener: StepMonitorListener,
    chunkListener: ChunkListener,
) {
    companion object {
        const val MONTHLY_CLEAN_STEP = "monthlyCleanStep"
        const val MONTHLY_AGGREGATE_STEP = "monthlyAggregateStep"
        const val MONTHLY_RANK_STEP = "monthlyRankStep"
        private const val MONTHLY_HOURLY_READER = "monthlyHourlyReader"
    }

    private val steps = ProductRankStepFactory(
        jobRepository = jobRepository,
        transactionManager = transactionManager,
        dataSource = dataSource,
        chunkSize = chunkSize,
        table = "product_metrics_monthly",
        mvTable = "mv_product_rank_monthly",
        periodResolver = RankingPeriod::monthlyOf,
        weights = weights,
        stepMonitorListener = stepMonitorListener,
        chunkListener = chunkListener,
    )

    @Bean(MONTHLY_CLEAN_STEP)
    fun monthlyCleanStep(): Step = steps.taskletStep(MONTHLY_CLEAN_STEP, monthlyCleanTasklet(null))

    @Bean
    @StepScope
    fun monthlyCleanTasklet(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Tasklet = steps.cleanTasklet(targetDate)

    @Bean(MONTHLY_AGGREGATE_STEP)
    fun monthlyAggregateStep(): Step = steps.aggregateStep(
        name = MONTHLY_AGGREGATE_STEP,
        reader = monthlyHourlyReader(null),
        processor = monthlyPeriodProcessor(null),
        writer = monthlyMetricsWriter(),
    )

    @Bean(MONTHLY_HOURLY_READER)
    @StepScope
    fun monthlyHourlyReader(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): JdbcPagingItemReader<ProductSignalSummary> = steps.hourlyReader(MONTHLY_HOURLY_READER, targetDate)

    @Bean
    @StepScope
    fun monthlyPeriodProcessor(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): ItemProcessor<ProductSignalSummary, ProductPeriodMetrics> = steps.periodProcessor(targetDate)

    @Bean
    fun monthlyMetricsWriter(): JdbcBatchItemWriter<ProductPeriodMetrics> = steps.metricsWriter()

    @Bean(MONTHLY_RANK_STEP)
    fun monthlyRankStep(): Step = steps.taskletStep(MONTHLY_RANK_STEP, monthlyRankTasklet(null))

    @Bean
    @StepScope
    fun monthlyRankTasklet(
        @Value("#{jobParameters['targetDate']}") targetDate: String?,
    ): Tasklet = steps.rankTasklet(targetDate)
}
