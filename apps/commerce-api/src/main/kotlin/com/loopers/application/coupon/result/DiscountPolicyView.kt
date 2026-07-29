package com.loopers.application.coupon.result

import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.PercentageDiscountPolicy

internal fun DiscountPolicy.toTypeValue(): Pair<DiscountType, Long> = when (this) {
    is FixedAmountDiscountPolicy -> DiscountType.FIXED to amount
    is PercentageDiscountPolicy -> DiscountType.RATE to percent
}
