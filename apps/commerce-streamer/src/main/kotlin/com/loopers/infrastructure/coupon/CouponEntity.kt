package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * `coupons` 테이블을 commerce-api 와 공유해 매핑한다(선착순 처리는 streamer 몫). 발급 한도·발급 수와 사용 가능 구간을 읽고,
 * 발급 수 소진은 원자 UPDATE(`CouponJpaRepository.increaseIssued`) 로 처리한다.
 */
@Entity
@Table(name = "coupons")
class CouponEntity private constructor(
    name: String,
    discountType: String,
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

    @Column(name = "discount_type", nullable = false)
    var discountType: String = discountType
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

    companion object {
        fun create(
            name: String = "선착순 쿠폰",
            discountType: String = "FIXED",
            discountValue: Long = 1000,
            minOrderAmount: Long? = null,
            issueStartAt: LocalDateTime,
            issueEndAt: LocalDateTime,
            useStartAt: LocalDateTime,
            useEndAt: LocalDateTime,
            issueLimit: Long?,
        ): CouponEntity = CouponEntity(
            name = name,
            discountType = discountType,
            discountValue = discountValue,
            minOrderAmount = minOrderAmount,
            issueStartAt = issueStartAt,
            issueEndAt = issueEndAt,
            useStartAt = useStartAt,
            useEndAt = useEndAt,
            issueLimit = issueLimit,
            issuedCount = 0L,
        )
    }
}
