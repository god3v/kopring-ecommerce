package com.loopers.application.order

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 정산 동시성 — 같은 결과가 콜백·폴링으로 동시에 도착해도(같은 거래 식별자로 settle 중복 호출)
 * 결제 행 비관 락이 정산을 직렬화해, 확정·보상은 정확히 한 번만 일어난다.
 */
@SpringBootTest
class PaymentSettleConcurrencyIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @MockkBean(relaxed = true)
    private lateinit var orderCompensator: OrderCompensator

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 거래 결과로 settle 을 동시에 여러 번 호출해도, 결제는 한 번만 확정되고 보상은 정확히 한 번만 일어난다.")
    @Test
    fun concurrentSettleSettlesExactlyOnce() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 1),
                ),
                idempotencyKey = "settle-once",
            ).also { it.markPaymentPending() },
        )
        paymentRepository.save(
            Payment.request(orderId = order.id, amount = 1000L).also { it.accept("tx-settle-1") },
        )

        val restores = AtomicInteger(0)
        every { orderCompensator.restore(any()) } answers {
            restores.incrementAndGet()
            // 경합 창을 넓힌다 — 락이 없으면 늦은 스레드도 REQUESTED 를 읽고 중복 보상한다.
            Thread.sleep(150)
        }

        val threads = 4
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            executor.submit {
                try {
                    startLatch.await()
                    paymentFacade.settle(PgTransaction("tx-settle-1", PgTransactionStatus.FAILED, "DECLINED"))
                } catch (_: Exception) {
                    // 경합 패자는 이미 정산된 결제를 보고 멱등 no-op — 정합성은 아래 최종 상태로 검증한다.
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 비관 락이 정산을 직렬화: 첫 호출만 확정·보상하고, 나머지는 이미 정산된 결제를 보고 거른다.
        assertThat(restores.get()).isEqualTo(1)
        assertThat(paymentRepository.findByTransactionId("tx-settle-1")!!.status).isEqualTo(PaymentStatus.FAILED)
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.PAYMENT_FAILED)
    }
}
