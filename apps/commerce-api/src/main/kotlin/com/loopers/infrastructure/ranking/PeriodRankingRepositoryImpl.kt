package com.loopers.infrastructure.ranking

import com.loopers.domain.ranking.PeriodRankingRepository
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingPeriod
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * 주간·월간 랭킹 MV 읽기 어댑터 — 기간에 따라 대상 테이블만 다르고 조회 형태는 같다.
 * rank_no 가 1부터 빈틈없이 매겨져 있어(ROW_NUMBER 적재) 순위 순 정렬이 곧 페이지 슬라이스다.
 */
@Component
class PeriodRankingRepositoryImpl(
    private val weeklyJpaRepository: ProductRankWeeklyMvJpaRepository,
    private val monthlyJpaRepository: ProductRankMonthlyMvJpaRepository,
) : PeriodRankingRepository {
    override fun topN(period: RankingPeriod, periodKey: String, page: Int, size: Int): List<RankedEntry> {
        if (size <= 0) return emptyList()
        val pageable = PageRequest.of(page, size)
        return when (period) {
            RankingPeriod.WEEKLY ->
                weeklyJpaRepository.findAllByPeriodKeyOrderByRankNoAsc(periodKey, pageable)
                    .map { RankedEntry(it.productId, it.score) }
            RankingPeriod.MONTHLY ->
                monthlyJpaRepository.findAllByPeriodKeyOrderByRankNoAsc(periodKey, pageable)
                    .map { RankedEntry(it.productId, it.score) }
            RankingPeriod.DAILY -> throw IllegalArgumentException("일간 랭킹은 Redis 랭킹판(RankingRepository)이 담당한다")
        }
    }

    override fun size(period: RankingPeriod, periodKey: String): Long = when (period) {
        RankingPeriod.WEEKLY -> weeklyJpaRepository.countByPeriodKey(periodKey)
        RankingPeriod.MONTHLY -> monthlyJpaRepository.countByPeriodKey(periodKey)
        RankingPeriod.DAILY -> throw IllegalArgumentException("일간 랭킹은 Redis 랭킹판(RankingRepository)이 담당한다")
    }
}
