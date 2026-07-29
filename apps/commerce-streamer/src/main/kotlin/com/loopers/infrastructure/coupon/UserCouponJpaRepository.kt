package com.loopers.infrastructure.coupon

import org.springframework.data.jpa.repository.JpaRepository

interface UserCouponJpaRepository : JpaRepository<UserCouponEntity, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
}
