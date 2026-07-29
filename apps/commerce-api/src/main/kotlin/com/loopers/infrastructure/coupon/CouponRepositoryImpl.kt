package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class CouponRepositoryImpl(
    private val couponJpaRepository: CouponJpaRepository,
) : CouponRepository {
    override fun save(coupon: Coupon): Coupon {
        val entity = if (coupon.id == 0L) {
            CouponEntity.from(coupon)
        } else {
            couponJpaRepository.findById(coupon.id)
                .orElseThrow { CoreException(CouponErrorType.COUPON_NOT_FOUND) }
                .apply { syncFrom(coupon) }
        }
        return couponJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Coupon? =
        couponJpaRepository.findByIdAndDeletedAtIsNull(id)?.toDomain()

    override fun findByIdIncludingDeleted(id: Long): Coupon? =
        couponJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(page: Int, size: Int): PageResult<Coupon> {
        val found = couponJpaRepository.findAllByDeletedAtIsNull(
            // createdAt 동률 시 페이지 간 중복/누락 방지를 위해 고유 tie-breaker(id)까지 정렬에 고정한다.
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
        )
        return PageResult(
            content = found.content.map { it.toDomain() },
            page = found.number,
            size = found.size,
            totalElements = found.totalElements,
            totalPages = found.totalPages,
        )
    }

    override fun findAllByIdsIncludingDeleted(ids: List<Long>): List<Coupon> {
        if (ids.isEmpty()) return emptyList()
        return couponJpaRepository.findAllByIdIn(ids).map { it.toDomain() }
    }
}
