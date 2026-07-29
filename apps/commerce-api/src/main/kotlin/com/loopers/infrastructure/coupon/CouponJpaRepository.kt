package com.loopers.infrastructure.coupon

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface CouponJpaRepository : JpaRepository<CouponEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): CouponEntity?
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<CouponEntity>
    fun findAllByIdIn(ids: List<Long>): List<CouponEntity>
}
