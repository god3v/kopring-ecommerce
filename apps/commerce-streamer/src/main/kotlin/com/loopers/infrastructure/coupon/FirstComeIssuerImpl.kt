package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.FirstComeIssuer
import com.loopers.domain.coupon.IssueOutcome
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 선착순 발급 원자 연산 어댑터. 중복 → 한도 원자 소진 → 발급 쿠폰 생성 순으로 시도한다.
 * 발급 수 증가는 조건부 UPDATE(`increaseIssued`) 라 한도를 넘지 못하고, 같은 회원 중복은 `user_coupons` UNIQUE 가 최종 방어한다.
 */
@Component
class FirstComeIssuerImpl(
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : FirstComeIssuer {
    override fun issue(userId: Long, couponId: Long, at: LocalDateTime): IssueOutcome {
        if (userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId)) {
            return IssueOutcome.Duplicate
        }
        if (couponJpaRepository.increaseIssued(couponId) == 0) {
            return IssueOutcome.SoldOut
        }
        val coupon = couponJpaRepository.findById(couponId)
            .orElseThrow { IllegalStateException("coupon not found: $couponId") }
        val issued = userCouponJpaRepository.save(
            UserCouponEntity.issue(
                userId = userId,
                couponId = couponId,
                issuedAt = at,
                usableFrom = coupon.useStartAt,
                expiredAt = coupon.useEndAt,
            ),
        )
        return IssueOutcome.Issued(issued.id)
    }
}
