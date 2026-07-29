package com.loopers.domain.coupon

import com.loopers.support.page.PageResult

interface UserCouponRepository {
    fun save(userCoupon: UserCoupon): UserCoupon
    fun findById(id: Long): UserCoupon?

    /**
     * 사용(소진) 경로 전용 — 행 단위 비관적 쓰기 락(`SELECT ... FOR UPDATE`) 으로 조회한다.
     * 같은 발급 쿠폰에 대한 동시 사용 시도를 직렬화해 이중 소진(Lost Update) 을 막는다.
     */
    fun findByIdForUpdate(id: Long): UserCoupon?

    /** 1인 1매 검사. */
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean

    /** 내 쿠폰 목록 — 발급 최신순. */
    fun findAllByUserId(userId: Long, page: Int, size: Int): PageResult<UserCoupon>

    /** 특정 템플릿의 발급 내역 — 발급 최신순. */
    fun findAllByCouponId(couponId: Long, page: Int, size: Int): PageResult<UserCoupon>
}
