package com.loopers.application.coupon.result

import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDateTime

data class CouponIssueResult(
    val userCouponId: Long,
    val userId: Long,
    val status: UserCouponStatus,
    val issuedAt: LocalDateTime,
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun of(userCoupon: UserCoupon, at: LocalDateTime): CouponIssueResult = CouponIssueResult(
            userCouponId = userCoupon.id,
            userId = userCoupon.userId,
            status = userCoupon.viewStatus(at),
            issuedAt = userCoupon.issuedAt,
            usedAt = userCoupon.usedAt,
        )
    }
}
