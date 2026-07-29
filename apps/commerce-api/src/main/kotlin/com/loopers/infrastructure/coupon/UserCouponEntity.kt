package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 발급 쿠폰 엔티티. `(user_id, coupon_id)` UNIQUE 로 1인 1매를 강제한다.
 * 저장되는 status 는 AVAILABLE / USED 둘뿐이다(EXPIRED 는 조회 시 파생).
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
    usedAt: LocalDateTime?,
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
    var usedAt: LocalDateTime? = usedAt
        protected set

    fun toDomain(): UserCoupon = UserCoupon(
        id = this.id,
        userId = this.userId,
        couponId = this.couponId,
        status = this.status,
        issuedAt = this.issuedAt,
        usableFrom = this.usableFrom,
        expiredAt = this.expiredAt,
        usedAt = this.usedAt,
    )

    fun syncFrom(userCoupon: UserCoupon) {
        this.status = userCoupon.status
        this.usedAt = userCoupon.usedAt
    }

    companion object {
        fun from(userCoupon: UserCoupon): UserCouponEntity = UserCouponEntity(
            userId = userCoupon.userId,
            couponId = userCoupon.couponId,
            status = userCoupon.status,
            issuedAt = userCoupon.issuedAt,
            usableFrom = userCoupon.usableFrom,
            expiredAt = userCoupon.expiredAt,
            usedAt = userCoupon.usedAt,
        )
    }
}
