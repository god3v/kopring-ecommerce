package com.loopers.domain.order

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("OrderService")
class OrderServiceTest {
    @Test
    @DisplayName("createOrder 는 라인 스냅샷이 박힌 Order 를 반환하고 각 product 의 deductStock 을 호출한다.")
    fun returnsOrderWithSnapshotAndDeductsStock() {
        // given
        val productA = Product.create(name = "A", price = 1000, stock = 10, likeCount = 0L, brandId = 1L)
        val productB = Product.create(name = "B", price = 500, stock = 5, likeCount = 0L, brandId = 1L)
        val products = mapOf(1L to productA, 2L to productB)
        val quantities = mapOf(1L to 2, 2L to 3)

        // when
        val order = OrderService.createOrder(
            userId = 42L,
            products = products,
            quantities = quantities,
            idempotencyKey = "abc",
        )

        // then
        assertThat(order.userId).isEqualTo(42L)
        assertThat(order.idempotencyKey).isEqualTo("abc")
        assertThat(order.lines).hasSize(2)
        assertThat(order.lines[0].productId).isEqualTo(1L)
        assertThat(order.lines[0].productName).isEqualTo("A")
        assertThat(order.lines[0].unitPrice).isEqualTo(1000)
        assertThat(order.lines[0].quantity.value).isEqualTo(2)
        assertThat(order.totalAmount).isEqualTo(2000 + 1500)
        assertThat(productA.stock.value).isEqualTo(8)
        assertThat(productB.stock.value).isEqualTo(2)
    }

    @Test
    @DisplayName("quantities 에 있는 productId 가 products 에 없으면 PRODUCT_NOT_FOUND 예외가 발생한다.")
    fun throwsProductNotFound_whenProductMissing() {
        // given
        val product = Product.create(name = "A", price = 1000, stock = 10, likeCount = 0L, brandId = 1L)
        val products = mapOf(1L to product)
        val quantities = mapOf(99L to 1)

        // when
        val ex = assertThrows<CoreException> {
            OrderService.createOrder(
                userId = 42L,
                products = products,
                quantities = quantities,
                idempotencyKey = "abc",
            )
        }

        // then
        assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
    }
}
