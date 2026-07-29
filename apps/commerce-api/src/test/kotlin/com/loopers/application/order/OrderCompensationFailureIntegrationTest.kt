package com.loopers.application.order

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 보상 대상 누락 시 롤백 통합 — 보상(`OrderCompensator.restore`) 대상 상품/쿠폰이 삭제·누락되면
 * 결제 실패 정산(`PaymentFacade.settle`) 이 조용히 부분 보상하지 않고 예외로 실패하며,
 * 같은 트랜잭션의 어떤 변경도 커밋되지 않는지(재고·주문·결제 상태 불변) 검증한다.
 */
@SpringBootTest
class OrderCompensationFailureIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val paymentRepository: PaymentRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    private val failTransaction = PgTransaction("tx-fail", PgTransactionStatus.FAILED, "DECLINED")

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("라인의 상품 하나가 누락되면 PRODUCT_NOT_FOUND 로 실패하고, 다른 라인 재고도 복원되지 않은 채 주문·결제가 그대로 남는다.")
    @Test
    fun rollsBackWhenProductMissing() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 8))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 2),
                    OrderLine.create(productId = MISSING_ID, productName = "유령상품", unitPrice = 1000, quantity = 1),
                ),
                idempotencyKey = "compensate-missing-product",
            ).also { it.markPaymentPending() },
        )
        val payment = paymentRepository.save(
            Payment.request(orderId = order.id, amount = order.totalAmount).also { it.accept("tx-fail") },
        )

        val ex = assertThrows<CoreException> { paymentFacade.settle(failTransaction) }

        assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        // 어떤 저장도 커밋되지 않는다 — 살아있는 상품 재고는 복원 전 그대로, 주문·결제 상태도 불변.
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(8)
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        assertThat(paymentRepository.findById(payment.id)!!.status).isEqualTo(PaymentStatus.REQUESTED)
    }

    @DisplayName("적용 쿠폰이 누락되면 USER_COUPON_NOT_FOUND 로 실패하고, 이미 saveAll 한 재고 복원까지 롤백되어 아무것도 커밋되지 않는다.")
    @Test
    fun rollsBackWhenCouponMissing() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 8))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 2),
                ),
                idempotencyKey = "compensate-missing-coupon",
                userCouponId = MISSING_ID,
            ).also { it.markPaymentPending() },
        )
        val payment = paymentRepository.save(
            Payment.request(orderId = order.id, amount = order.totalAmount).also { it.accept("tx-fail") },
        )

        val ex = assertThrows<CoreException> { paymentFacade.settle(failTransaction) }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.USER_COUPON_NOT_FOUND)
        // 쿠폰 가드는 재고 saveAll 이후에 던져지지만, 트랜잭션 롤백으로 재고 복원도 커밋되지 않는다(8 그대로).
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(8)
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.PAYMENT_PENDING)
        assertThat(paymentRepository.findById(payment.id)!!.status).isEqualTo(PaymentStatus.REQUESTED)
    }

    @DisplayName("주문 이후 상품이 soft-delete 되어도, 주문 스냅샷 기준으로 재고를 복원하고 주문을 PAYMENT_FAILED 로 마감한다.")
    @Test
    fun restoresStockEvenWhenProductSoftDeleted() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 8))
        // 주문 이후 상품이 delist(soft-delete) 된 상황을 만든다 — @SQLRestriction 으로 일반 조회에선 사라진다.
        product.softDelete()
        productRepository.save(product)

        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 2),
                ),
                idempotencyKey = "compensate-soft-deleted",
            ).also { it.markPaymentPending() },
        )
        val payment = paymentRepository.save(
            Payment.request(orderId = order.id, amount = order.totalAmount).also { it.accept("tx-fail") },
        )

        paymentFacade.settle(failTransaction) // 누락으로 보지 않고 통과해야 한다(예외 없음).

        // delist 된 상품도 스냅샷에 박힌 라인 기준으로 재고가 복원되고, 주문·결제는 정상 마감된다.
        assertThat(stockOf(product.id)).isEqualTo(10)
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        assertThat(paymentRepository.findById(payment.id)!!.status).isEqualTo(PaymentStatus.FAILED)
    }

    /** @SQLRestriction 을 우회해 soft-delete 된 행의 재고까지 직접 읽는다. */
    private fun stockOf(productId: Long): Int =
        jdbcTemplate.queryForObject("SELECT stock FROM products WHERE id = ?", Int::class.javaObjectType, productId)!!

    companion object {
        /** 어떤 상품/쿠폰에도 매핑되지 않는 식별자. */
        private const val MISSING_ID = 999_999L
    }
}
