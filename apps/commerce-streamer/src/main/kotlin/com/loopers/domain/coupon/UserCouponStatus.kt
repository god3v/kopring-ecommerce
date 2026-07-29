package com.loopers.domain.coupon

/**
 * 발급 쿠폰 상태. commerce-api 와 같은 `user_coupons.status` 컬럼을 공유하므로 enum 이름이 일치해야 한다.
 * 선착순 발급이 만드는 값은 AVAILABLE 뿐이다.
 */
enum class UserCouponStatus {
    AVAILABLE,
    USED,
    EXPIRED,
}
