package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component

@Component
class UserCouponRepositoryImpl(
    private val userCouponJpaRepository: UserCouponJpaRepository,
) : UserCouponRepository {
    override fun save(userCoupon: UserCoupon): UserCoupon {
        val entity = if (userCoupon.id == 0L) {
            UserCouponEntity.from(userCoupon)
        } else {
            userCouponJpaRepository.findById(userCoupon.id)
                .orElseThrow { CoreException(CouponErrorType.USER_COUPON_NOT_FOUND) }
                .apply { syncFrom(userCoupon) }
        }
        return userCouponJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): UserCoupon? =
        userCouponJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByIdForUpdate(id: Long): UserCoupon? =
        userCouponJpaRepository.findByIdForUpdate(id)?.toDomain()

    override fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean =
        userCouponJpaRepository.existsByUserIdAndCouponId(userId, couponId)

    override fun findAllByUserId(userId: Long, page: Int, size: Int): PageResult<UserCoupon> =
        userCouponJpaRepository.findAllByUserId(userId, issuedAtDesc(page, size)).toPageResult()

    override fun findAllByCouponId(couponId: Long, page: Int, size: Int): PageResult<UserCoupon> =
        userCouponJpaRepository.findAllByCouponId(couponId, issuedAtDesc(page, size)).toPageResult()

    private fun issuedAtDesc(page: Int, size: Int): PageRequest =
        // issuedAt 동률 시 페이지 간 중복/누락 방지를 위해 고유 tie-breaker(id)까지 정렬에 고정한다.
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("issuedAt"), Sort.Order.desc("id")))

    private fun Page<UserCouponEntity>.toPageResult(): PageResult<UserCoupon> = PageResult(
        content = content.map { it.toDomain() },
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
}
