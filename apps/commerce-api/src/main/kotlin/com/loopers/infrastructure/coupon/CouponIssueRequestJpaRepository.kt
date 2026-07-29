package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestEntity, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequestEntity?
}
