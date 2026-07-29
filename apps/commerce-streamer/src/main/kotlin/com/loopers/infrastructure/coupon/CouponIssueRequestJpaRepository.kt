package com.loopers.infrastructure.coupon

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponIssueRequestJpaRepository : JpaRepository<CouponIssueRequestEntity, Long> {
    fun findByRequestId(requestId: String): CouponIssueRequestEntity?

    /** 처리 경로 전용 비관적 쓰기 락 조회 — 같은 요청의 동시 처리(중복 배달)를 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from CouponIssueRequestEntity r where r.requestId = :requestId")
    fun findByRequestIdForUpdate(@Param("requestId") requestId: String): CouponIssueRequestEntity?
}
