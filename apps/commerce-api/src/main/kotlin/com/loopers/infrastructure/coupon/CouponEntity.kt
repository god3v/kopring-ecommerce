package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponName
import com.loopers.domain.coupon.DiscountPolicy
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.FixedAmountDiscountPolicy
import com.loopers.domain.coupon.PercentageDiscountPolicy
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 쿠폰 템플릿 엔티티.
 *
 * `@SQLRestriction` 을 두지 않는다 — 발급 쿠폰의 사용·조회 경로는 삭제 마크된 템플릿도
 * 조회해야 하므로, 삭제 필터링은 리포지토리 메서드에서 명시적으로 처리한다.
 */
@Entity
@Table(name = "coupons")
class CouponEntity private constructor(
    name: String,
    discountType: DiscountType,
    discountValue: Long,
    minOrderAmount: Long?,
    issueStartAt: LocalDateTime,
    issueEndAt: LocalDateTime,
    useStartAt: LocalDateTime,
    useEndAt: LocalDateTime,
    issueLimit: Long?,
    issuedCount: Long,
) : BaseEntity() {
    @Column(nullable = false)
    var name: String = name
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    var discountType: DiscountType = discountType
        protected set

    @Column(name = "discount_value", nullable = false)
    var discountValue: Long = discountValue
        protected set

    @Column(name = "min_order_amount")
    var minOrderAmount: Long? = minOrderAmount
        protected set

    @Column(name = "issue_start_at", nullable = false)
    var issueStartAt: LocalDateTime = issueStartAt
        protected set

    @Column(name = "issue_end_at", nullable = false)
    var issueEndAt: LocalDateTime = issueEndAt
        protected set

    @Column(name = "use_start_at", nullable = false)
    var useStartAt: LocalDateTime = useStartAt
        protected set

    @Column(name = "use_end_at", nullable = false)
    var useEndAt: LocalDateTime = useEndAt
        protected set

    @Column(name = "issue_limit")
    var issueLimit: Long? = issueLimit
        protected set

    @Column(name = "issued_count", nullable = false)
    var issuedCount: Long = issuedCount
        protected set

    fun toDomain(): Coupon = Coupon(
        id = this.id,
        name = CouponName.of(this.name),
        discountPolicy = DiscountPolicy.of(this.discountType, this.discountValue),
        minOrderAmount = this.minOrderAmount,
        issueStartAt = this.issueStartAt,
        issueEndAt = this.issueEndAt,
        useStartAt = this.useStartAt,
        useEndAt = this.useEndAt,
        issueLimit = this.issueLimit,
        issuedCount = this.issuedCount,
    )

    fun syncFrom(coupon: Coupon) {
        val (type, value) = coupon.discountPolicy.toColumns()
        this.name = coupon.name.value
        this.discountType = type
        this.discountValue = value
        this.minOrderAmount = coupon.minOrderAmount
        this.issueStartAt = coupon.issueStartAt
        this.issueEndAt = coupon.issueEndAt
        this.useStartAt = coupon.useStartAt
        this.useEndAt = coupon.useEndAt
        if (coupon.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(coupon: Coupon): CouponEntity {
            val (type, value) = coupon.discountPolicy.toColumns()
            return CouponEntity(
                name = coupon.name.value,
                discountType = type,
                discountValue = value,
                minOrderAmount = coupon.minOrderAmount,
                issueStartAt = coupon.issueStartAt,
                issueEndAt = coupon.issueEndAt,
                useStartAt = coupon.useStartAt,
                useEndAt = coupon.useEndAt,
                issueLimit = coupon.issueLimit,
                issuedCount = coupon.issuedCount,
            )
        }
    }
}

private fun DiscountPolicy.toColumns(): Pair<DiscountType, Long> = when (this) {
    is FixedAmountDiscountPolicy -> DiscountType.FIXED to amount
    is PercentageDiscountPolicy -> DiscountType.RATE to percent
}
