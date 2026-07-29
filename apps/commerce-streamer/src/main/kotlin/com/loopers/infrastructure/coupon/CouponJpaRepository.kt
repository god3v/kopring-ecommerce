package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CouponJpaRepository : JpaRepository<CouponEntity, Long> {
    /**
     * 발급 수를 원자적으로 1 늘린다 — 한도 미만일 때만 성공한다(영향 행 1). 한도 소진이면 0 을 반환한다.
     * 조건부 UPDATE 라 동시 요청이 몰려도 발급 수는 한도를 넘지 못한다(락 없이 정합성 보장).
     */
    @Modifying(clearAutomatically = true)
    @Query(
        "update CouponEntity c set c.issuedCount = c.issuedCount + 1 " +
            "where c.id = :couponId and (c.issueLimit is null or c.issuedCount < c.issueLimit)",
    )
    fun increaseIssued(@Param("couponId") couponId: Long): Int
}
