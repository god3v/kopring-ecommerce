package com.loopers.application.coupon.result

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDateTime

data class IssuedCouponResult(
    val userCouponId: Long,
    val couponId: Long,
    val name: String,
    val type: DiscountType,
    val value: Long,
    val minOrderAmount: Long?,
    val usableFrom: LocalDateTime,
    val expiredAt: LocalDateTime,
    val status: UserCouponStatus,
    val issuedAt: LocalDateTime,
) {
    companion object {
        fun of(userCoupon: UserCoupon, coupon: Coupon): IssuedCouponResult {
            val (type, value) = coupon.discountPolicy.toTypeValue()
            return IssuedCouponResult(
                userCouponId = userCoupon.id,
                couponId = coupon.id,
                name = coupon.name.value,
                type = type,
                value = value,
                minOrderAmount = coupon.minOrderAmount,
                usableFrom = userCoupon.usableFrom,
                expiredAt = userCoupon.expiredAt,
                status = userCoupon.status,
                issuedAt = userCoupon.issuedAt,
            )
        }
    }
}
