package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

@JvmInline
value class CouponName private constructor(val value: String) {
    companion object {
        fun of(value: String): CouponName {
            if (value.isBlank()) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "쿠폰 이름은 비어 있을 수 없다.")
            }
            return CouponName(value)
        }
    }
}
