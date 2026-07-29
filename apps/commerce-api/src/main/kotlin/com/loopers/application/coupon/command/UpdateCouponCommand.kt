package com.loopers.application.coupon.command

import com.loopers.domain.coupon.DiscountType
import java.time.LocalDateTime

data class UpdateCouponCommand(
    val name: String,
    val discountType: DiscountType,
    val discountValue: Long,
    val minOrderAmount: Long?,
    val issueStartAt: LocalDateTime,
    val issueEndAt: LocalDateTime,
    val useStartAt: LocalDateTime,
    val useEndAt: LocalDateTime,
)
