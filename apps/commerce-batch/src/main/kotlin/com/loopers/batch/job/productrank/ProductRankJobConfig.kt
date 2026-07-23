package com.loopers.batch.job.productrank

import com.loopers.batch.listener.JobListener
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 주간·월간 랭킹 집계 잡 — targetDate(yyyy-MM-dd) 가 속한 ISO 주·달력 월을 통째로 재집계한다.
 * 쓰기가 기간 키 단위 delete 후 insert 라 몇 번 실행해도 결과가 같고,
 * RunIdIncrementer 가 같은 targetDate 의 정정 재실행을 새 JobInstance 로 연다.
 */
@ConditionalOnProperty(name = ["spring.batch.job.name"], havingValue = ProductRankJob.NAME)
@Configuration
class ProductRankJobConfig(
    private val jobRepository: JobRepository,
    private val jobListener: JobListener,
) {
    @Bean(ProductRankJob.NAME)
    fun productRankJob(
        @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_CLEAN_STEP) weeklyCleanStep: Step,
        @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_AGGREGATE_STEP) weeklyAggregateStep: Step,
        @Qualifier(ProductRankWeeklyStepConfig.WEEKLY_RANK_STEP) weeklyRankStep: Step,
        @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_CLEAN_STEP) monthlyCleanStep: Step,
        @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_AGGREGATE_STEP) monthlyAggregateStep: Step,
        @Qualifier(ProductRankMonthlyStepConfig.MONTHLY_RANK_STEP) monthlyRankStep: Step,
    ): Job = JobBuilder(ProductRankJob.NAME, jobRepository)
        .incrementer(RunIdIncrementer())
        .validator(TargetDateJobParametersValidator())
        .listener(jobListener)
        .start(weeklyCleanStep)
        .next(weeklyAggregateStep)
        .next(weeklyRankStep)
        .next(monthlyCleanStep)
        .next(monthlyAggregateStep)
        .next(monthlyRankStep)
        .build()
}
