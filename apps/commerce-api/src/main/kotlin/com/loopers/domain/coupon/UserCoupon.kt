package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import java.time.LocalDateTime
import java.time.ZoneId

class UserCoupon internal constructor(
    val id: Long = 0L,
    val userId: Long,
    val couponId: Long,
    status: UserCouponStatus = UserCouponStatus.AVAILABLE,
    val issuedAt: LocalDateTime,
    val usableFrom: LocalDateTime,
    val expiredAt: LocalDateTime,
    usedAt: LocalDateTime? = null,
) {
    var status: UserCouponStatus = status
        private set

    var usedAt: LocalDateTime? = usedAt
        private set

    /** 소유자 검증 — 타인의 쿠폰이면 존재를 드러내지 않도록 NOT_FOUND 로 거부한다. */
    fun ensureOwnedBy(userId: Long) {
        if (this.userId != userId) {
            throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND, "본인의 쿠폰이 아니다.")
        }
    }

    fun use(at: LocalDateTime) {
        if (isUsed()) {
            throw CoreException(CouponErrorType.ALREADY_USED_COUPON, "이미 사용된 쿠폰")
        }
        if (at.isBefore(usableFrom) || at.isAfter(expiredAt)) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "사용 가능 기간이 아님")
        }
        status = UserCouponStatus.USED
        usedAt = at
    }

    fun isUsed(): Boolean = status == UserCouponStatus.USED

    fun cancelUse() {
        if (isUsed()) {
            status = UserCouponStatus.AVAILABLE
            usedAt = null
        }
    }

    fun viewStatus(at: LocalDateTime): UserCouponStatus = when {
        status == UserCouponStatus.USED -> UserCouponStatus.USED
        at.isAfter(expiredAt) -> UserCouponStatus.EXPIRED
        else -> UserCouponStatus.AVAILABLE
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun issue(
            userId: Long,
            couponId: Long,
            usableFrom: LocalDateTime,
            expiredAt: LocalDateTime,
        ): UserCoupon = UserCoupon(
            userId = userId,
            couponId = couponId,
            status = UserCouponStatus.AVAILABLE,
            issuedAt = LocalDateTime.now(SEOUL),
            usableFrom = usableFrom,
            expiredAt = expiredAt,
        )
    }
}
