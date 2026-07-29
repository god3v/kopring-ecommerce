package com.loopers.application.order

import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.Order
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component

/**
 * 주문 보상 — 차감 재고를 복원하고 소진 쿠폰을 AVAILABLE 로 되돌린다.
 * 결제 실패 정산(`PaymentFacade.settle`)이 호출자의 트랜잭션 경계 안에서 사용한다.
 */
@Component
class OrderCompensator(
    private val productRepository: ProductRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    fun restore(order: Order) {
        // 차감과 같은 비관 락으로 직렬화하고, soft-delete 된 상품도 함께 잠근다(재고는 되돌려야 하므로).
        // 행 자체가 없으면 부분 보상 대신 즉시 실패시켜 롤백한다.
        val products = productRepository.findAllByIdsForUpdateIncludingDeleted(
            order.lines.map { it.productId },
        ).associateBy { it.id }
        order.lines.forEach { line ->
            val product = products[line.productId]
                ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND, "보상 대상 상품(${line.productId})을 찾을 수 없어 재고를 복원할 수 없다.")
            product.restoreStock(line.quantity.value)
        }
        productRepository.saveAll(products.values)

        // 적용 쿠폰도 누락되면 USED 로 영영 남으므로 조용히 끝내지 않고 실패시킨다.
        order.userCouponId?.let { userCouponId ->
            val userCoupon = userCouponRepository.findByIdForUpdate(userCouponId)
                ?: throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND, "보상 대상 쿠폰($userCouponId)을 찾을 수 없어 사용 취소할 수 없다.")
            userCoupon.cancelUse()
            userCouponRepository.save(userCoupon)
        }
    }
}
