package com.loopers.infrastructure.ranking

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface ProductRankWeeklyMvJpaRepository : JpaRepository<ProductRankWeeklyMvEntity, Long> {
    fun findAllByPeriodKeyOrderByRankNoAsc(periodKey: String, pageable: Pageable): List<ProductRankWeeklyMvEntity>

    fun countByPeriodKey(periodKey: String): Long
}

interface ProductRankMonthlyMvJpaRepository : JpaRepository<ProductRankMonthlyMvEntity, Long> {
    fun findAllByPeriodKeyOrderByRankNoAsc(periodKey: String, pageable: Pageable): List<ProductRankMonthlyMvEntity>

    fun countByPeriodKey(periodKey: String): Long
}
