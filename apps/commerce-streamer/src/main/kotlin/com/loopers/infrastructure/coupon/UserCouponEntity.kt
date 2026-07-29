package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.UserCouponStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * `user_coupons` 테이블을 commerce-api 와 공유해 매핑한다. 선착순 발급 성공 시 이 행을 새로 넣는다.
 * `(user_id, coupon_id)` UNIQUE 가 1인 1매를 강제한다 — 동시 중복 요청은 두 번째 INSERT 가 실패한다.
 */
@Entity
@Table(
    name = "user_coupons",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_coupons_user_coupon", columnNames = ["user_id", "coupon_id"]),
    ],
)
class UserCouponEntity private constructor(
    userId: Long,
    couponId: Long,
    status: UserCouponStatus,
    issuedAt: LocalDateTime,
    usableFrom: LocalDateTime,
    expiredAt: LocalDateTime,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserCouponStatus = status
        protected set

    @Column(name = "issued_at", nullable = false)
    var issuedAt: LocalDateTime = issuedAt
        protected set

    @Column(name = "usable_from", nullable = false)
    var usableFrom: LocalDateTime = usableFrom
        protected set

    @Column(name = "expired_at", nullable = false)
    var expiredAt: LocalDateTime = expiredAt
        protected set

    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null
        protected set

    companion object {
        fun issue(
            userId: Long,
            couponId: Long,
            issuedAt: LocalDateTime,
            usableFrom: LocalDateTime,
            expiredAt: LocalDateTime,
        ): UserCouponEntity = UserCouponEntity(
            userId = userId,
            couponId = couponId,
            status = UserCouponStatus.AVAILABLE,
            issuedAt = issuedAt,
            usableFrom = usableFrom,
            expiredAt = expiredAt,
        )
    }
}
