package com.loopers.domain.coupon

import com.loopers.support.page.PageResult

interface CouponRepository {
    fun save(coupon: Coupon): Coupon

    /** soft-deleted 제외. 관리자 상세·수정·삭제·발급 경로. */
    fun findById(id: Long): Coupon?

    /** soft-deleted 포함. 이미 발급된 쿠폰의 사용 경로 — 템플릿이 이후 삭제돼도 조회된다. */
    fun findByIdIncludingDeleted(id: Long): Coupon?

    /** soft-deleted 제외, 최신순. 관리자 목록. */
    fun findAll(page: Int, size: Int): PageResult<Coupon>

    /** soft-deleted 포함. 내 쿠폰 목록의 템플릿 조인 — 삭제된 템플릿의 발급 쿠폰도 노출한다. */
    fun findAllByIdsIncludingDeleted(ids: List<Long>): List<Coupon>
}
