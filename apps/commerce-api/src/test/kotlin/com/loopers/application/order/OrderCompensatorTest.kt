package com.loopers.application.order

import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("OrderCompensator — 재고·쿠폰 보상")
class OrderCompensatorTest {
    private val productRepository: ProductRepository = mockk()
    private val userCouponRepository: UserCouponRepository = mockk()
    private val compensator = OrderCompensator(productRepository, userCouponRepository)

    private fun order(userCouponId: Long? = null) = Order.create(
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        idempotencyKey = "k",
        userCouponId = userCouponId,
    )

    @DisplayName("차감했던 재고를 라인 수량만큼 복원하고, 적용 쿠폰을 AVAILABLE 로 되돌린다.")
    @Test
    fun restoresStockAndCoupon() {
        val product = ProductFixture.validProduct(id = 1L, name = "운동화", price = 1000, stock = 10)
        val userCoupon = CouponFixture.userCoupon(id = 99L, userId = 1L, couponId = 7L, status = UserCouponStatus.USED)
        every { productRepository.findAllByIdsForUpdateIncludingDeleted(listOf(1L)) } returns listOf(product)
        every { productRepository.saveAll(any()) } answers { firstArg<Collection<Product>>().toList() }
        every { userCouponRepository.findByIdForUpdate(99L) } returns userCoupon
        every { userCouponRepository.save(any()) } answers { firstArg() }

        compensator.restore(order(userCouponId = 99L))

        assertThat(product.stock.value).isEqualTo(12) // 10 + 라인 수량 2 원복
        assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("쿠폰 미적용 주문은 재고만 복원한다 (쿠폰 조회 없음).")
    @Test
    fun restoresStockOnlyWhenNoCoupon() {
        val product = ProductFixture.validProduct(id = 1L, name = "운동화", price = 1000, stock = 10)
        every { productRepository.findAllByIdsForUpdateIncludingDeleted(listOf(1L)) } returns listOf(product)
        every { productRepository.saveAll(any()) } answers { firstArg<Collection<Product>>().toList() }

        compensator.restore(order(userCouponId = null))

        assertThat(product.stock.value).isEqualTo(12)
        verify(exactly = 0) { userCouponRepository.findByIdForUpdate(any()) }
    }

    @DisplayName("주문 라인의 상품이 조회되지 않으면 PRODUCT_NOT_FOUND 로 실패하고, 어떤 재고도 저장하지 않는다.")
    @Test
    fun failsWhenProductMissing() {
        // 삭제 마크 포함 조회로도 행이 없는 상황(진짜 정합성 손상) — 조용히 건너뛰면 부분 보상이 된다.
        every { productRepository.findAllByIdsForUpdateIncludingDeleted(listOf(1L)) } returns emptyList()

        val ex = assertThrows<CoreException> { compensator.restore(order()) }

        assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        verify(exactly = 0) { productRepository.saveAll(any()) }
    }

    @DisplayName("적용 쿠폰이 조회되지 않으면 USER_COUPON_NOT_FOUND 로 실패하고, 쿠폰을 저장하지 않는다.")
    @Test
    fun failsWhenCouponMissing() {
        val product = ProductFixture.validProduct(id = 1L, name = "운동화", price = 1000, stock = 10)
        every { productRepository.findAllByIdsForUpdateIncludingDeleted(listOf(1L)) } returns listOf(product)
        every { productRepository.saveAll(any()) } answers { firstArg<Collection<Product>>().toList() }
        every { userCouponRepository.findByIdForUpdate(99L) } returns null

        val ex = assertThrows<CoreException> { compensator.restore(order(userCouponId = 99L)) }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.USER_COUPON_NOT_FOUND)
        verify(exactly = 0) { userCouponRepository.save(any()) }
    }
}
