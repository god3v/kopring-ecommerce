package com.loopers.domain.coupon

import com.loopers.support.error.CoreException

enum class UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    ;

    companion object {
        fun from(value: String): UserCouponStatus =
            entries.firstOrNull { it.name == value }
                ?: throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "지원하지 않는 쿠폰 상태이다.")
    }
}
