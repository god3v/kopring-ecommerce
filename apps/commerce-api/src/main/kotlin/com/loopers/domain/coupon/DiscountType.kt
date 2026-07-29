package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

enum class DiscountType {
    FIXED,
    RATE,
    ;

    companion object {
        fun from(value: String): DiscountType =
            entries.firstOrNull { it.name == value }
                ?: throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "지원하지 않는 할인 종류")
    }
}
