package com.loopers.application.coupon.result

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import java.time.LocalDateTime

data class MyCouponResult(
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
    val usedAt: LocalDateTime?,
) {
    companion object {
        fun of(userCoupon: UserCoupon, coupon: Coupon, at: LocalDateTime): MyCouponResult {
            val (type, value) = coupon.discountPolicy.toTypeValue()
            return MyCouponResult(
                userCouponId = userCoupon.id,
                couponId = coupon.id,
                name = coupon.name.value,
                type = type,
                value = value,
                minOrderAmount = coupon.minOrderAmount,
                usableFrom = userCoupon.usableFrom,
                expiredAt = userCoupon.expiredAt,
                status = userCoupon.viewStatus(at),
                issuedAt = userCoupon.issuedAt,
                usedAt = userCoupon.usedAt,
            )
        }
    }
}
