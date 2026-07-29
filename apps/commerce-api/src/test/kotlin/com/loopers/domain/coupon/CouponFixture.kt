package com.loopers.domain.coupon

import java.time.LocalDateTime

/**
 * 테스트용 쿠폰 픽스처. internal 생성자를 직접 사용해 create 의 미래-만료 검증을 우회한다
 * (만료된 쿠폰 시나리오를 구성하기 위함).
 */
object CouponFixture {
    const val DEFAULT_NAME = "신규가입 10% 할인"

    fun coupon(
        id: Long = 0L,
        name: String = DEFAULT_NAME,
        discountType: DiscountType = DiscountType.RATE,
        discountValue: Long = 10,
        minOrderAmount: Long? = null,
        issueStartAt: LocalDateTime = LocalDateTime.now().minusDays(1),
        issueEndAt: LocalDateTime = LocalDateTime.now().plusDays(30),
        useStartAt: LocalDateTime = LocalDateTime.now().minusDays(1),
        useEndAt: LocalDateTime = LocalDateTime.now().plusDays(30),
        issueLimit: Long? = null,
        issuedCount: Long = 0L,
    ): Coupon = Coupon(
        id = id,
        name = CouponName.of(name),
        discountPolicy = DiscountPolicy.of(discountType, discountValue),
        minOrderAmount = minOrderAmount,
        issueStartAt = issueStartAt,
        issueEndAt = issueEndAt,
        useStartAt = useStartAt,
        useEndAt = useEndAt,
        issueLimit = issueLimit,
        issuedCount = issuedCount,
    )

    fun userCoupon(
        id: Long = 0L,
        userId: Long = 1L,
        couponId: Long = 1L,
        status: UserCouponStatus = UserCouponStatus.AVAILABLE,
        issuedAt: LocalDateTime = LocalDateTime.now(),
        usableFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(30),
        usedAt: LocalDateTime? = null,
    ): UserCoupon = UserCoupon(
        id = id,
        userId = userId,
        couponId = couponId,
        status = status,
        issuedAt = issuedAt,
        usableFrom = usableFrom,
        expiredAt = expiredAt,
        usedAt = usedAt,
    )
}
