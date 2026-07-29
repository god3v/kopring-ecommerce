package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Coupon internal constructor(
    val id: Long = 0L,
    name: CouponName,
    discountPolicy: DiscountPolicy,
    minOrderAmount: Long?,
    issueStartAt: LocalDateTime,
    issueEndAt: LocalDateTime,
    useStartAt: LocalDateTime,
    useEndAt: LocalDateTime,
    issueLimit: Long? = null,
    issuedCount: Long = 0L,
) {
    var name: CouponName = name
        private set

    var discountPolicy: DiscountPolicy = discountPolicy
        private set

    var minOrderAmount: Long? = minOrderAmount
        private set

    var issueStartAt: LocalDateTime = issueStartAt
        private set

    var issueEndAt: LocalDateTime = issueEndAt
        private set

    var useStartAt: LocalDateTime = useStartAt
        private set

    var useEndAt: LocalDateTime = useEndAt
        private set

    var issueLimit: Long? = issueLimit
        private set

    var issuedCount: Long = issuedCount
        private set

    var deletedAt: LocalDateTime? = null
        private set

    /** 선착순 발급(접수) 대상인지 확인한다 — 발급 한도가 없는(무제한) 템플릿이면 거부한다. */
    fun ensureFirstCome() {
        if (issueLimit == null) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "선착순 발급 대상이 아닌 템플릿")
        }
    }

    /** 즉시 발급 대상인지 확인한다 — 발급 한도가 있는(선착순 전용) 템플릿이면 거부한다. */
    fun ensureNotFirstCome() {
        if (issueLimit != null) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "선착순 전용 템플릿은 즉시 발급할 수 없다")
        }
    }

    /**
     * 선착순 발급 한 장을 소진한다. 한도에 도달했으면 품절로 거부한다(발급 수는 그대로).
     * 한도가 없는(선착순 아닌) 템플릿은 상한 없이 카운트만 증가한다.
     */
    fun issue() {
        val limit = issueLimit
        if (limit != null && issuedCount >= limit) {
            throw CoreException(CouponErrorType.COUPON_SOLD_OUT, "발급 한도 소진")
        }
        issuedCount += 1
    }

    fun calculateDiscount(orderAmount: Long): Long {
        val min = minOrderAmount
        if (min != null && orderAmount < min) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "최소 주문 금액 미달")
        }
        return discountPolicy.discountFor(orderAmount)
    }

    fun ensureIssuable(now: LocalDateTime) {
        if (now.isBefore(issueStartAt) || now.isAfter(issueEndAt)) {
            throw CoreException(CouponErrorType.COUPON_NOT_APPLICABLE, "발급 가능 기간 아님")
        }
    }

    fun update(
        name: String,
        discountType: DiscountType,
        discountValue: Long,
        minOrderAmount: Long?,
        issueStartAt: LocalDateTime,
        issueEndAt: LocalDateTime,
        useStartAt: LocalDateTime,
        useEndAt: LocalDateTime,
        now: LocalDateTime,
    ) {
        validate(minOrderAmount, issueStartAt, issueEndAt, useStartAt, useEndAt, now)
        this.name = CouponName.of(name)
        this.discountPolicy = DiscountPolicy.of(discountType, discountValue)
        this.minOrderAmount = minOrderAmount
        this.issueStartAt = issueStartAt
        this.issueEndAt = issueEndAt
        this.useStartAt = useStartAt
        this.useEndAt = useEndAt
    }

    fun softDelete(now: LocalDateTime) {
        if (deletedAt == null) {
            deletedAt = now
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    companion object {
        private fun validate(
            minOrderAmount: Long?,
            issueStartAt: LocalDateTime,
            issueEndAt: LocalDateTime,
            useStartAt: LocalDateTime,
            useEndAt: LocalDateTime,
            now: LocalDateTime,
        ) {
            if (minOrderAmount != null && minOrderAmount < 0L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "최소 주문 금액은 음수가 될 수 없다.")
            }
            if (!issueEndAt.isAfter(issueStartAt)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "발급 종료 시각은 발급 시작 시각 이후여야 한다.")
            }
            if (!useEndAt.isAfter(useStartAt)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "사용 종료 시각은 사용 시작 시각 이후여야 한다.")
            }
            if (!issueEndAt.isAfter(now)) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "발급 종료 시각은 미래여야 한다.")
            }
        }

        fun create(
            name: String,
            discountType: DiscountType,
            discountValue: Long,
            minOrderAmount: Long?,
            issueStartAt: LocalDateTime,
            issueEndAt: LocalDateTime,
            useStartAt: LocalDateTime,
            useEndAt: LocalDateTime,
            now: LocalDateTime,
            issueLimit: Long? = null,
        ): Coupon {
            validate(minOrderAmount, issueStartAt, issueEndAt, useStartAt, useEndAt, now)
            if (issueLimit != null && issueLimit <= 0L) {
                throw CoreException(CouponErrorType.COUPON_BAD_REQUEST, "발급 한도는 1 이상이어야 한다.")
            }
            return Coupon(
                name = CouponName.of(name),
                discountPolicy = DiscountPolicy.of(discountType, discountValue),
                minOrderAmount = minOrderAmount,
                issueStartAt = issueStartAt,
                issueEndAt = issueEndAt,
                useStartAt = useStartAt,
                useEndAt = useEndAt,
                issueLimit = issueLimit,
            )
        }
    }
}
