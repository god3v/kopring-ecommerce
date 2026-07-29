package com.loopers.application.order

import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
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
 * 결제 요청 동시성 — 같은 결제대기 주문에 pay() 가 동시·중복으로 들어와도
 * 주문 행 비관 락이 REQUESTED 생성을 직렬화해, 결제는 1건만 만들어지고 외부 PG 요청도 정확히 한 번만 일어난다.
 * (재시도/중복 트리거 시의 이중 청구를 막는 가드.)
 */
@SpringBootTest
class PaymentConcurrencyIntegrationTest @Autowired constructor(
    private val paymentFacade: PaymentFacade,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("같은 결제대기 주문에 pay() 를 동시에 여러 번 호출해도, REQUESTED 결제는 1건이고 외부 PG 요청은 정확히 한 번만 일어난다.")
    @Test
    fun concurrentPayRequestsExactlyOnce() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 10))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(
                    OrderLine.create(productId = product.id, productName = "운동화", unitPrice = 1000, quantity = 1),
                ),
                idempotencyKey = "pay-once",
            ),
        )

        val requests = AtomicInteger(0)
        every { paymentGateway.request(any()) } answers {
            requests.incrementAndGet()
            // 경합 창을 넓힌다 — 락이 없으면 늦은 스레드도 PENDING 을 읽고 중복 요청한다.
            Thread.sleep(150)
            PaymentResponse(transactionKey = "tx-${order.id}", status = PgTransactionStatus.PENDING)
        }

        val threads = 4
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            executor.submit {
                try {
                    startLatch.await()
                    paymentFacade.pay(
                        PaymentCommand(
                            userId = user.id.toString(),
                            orderId = order.id,
                            cardType = "SAMSUNG",
                            cardNo = "1234-5678-9814-1451",
                        ),
                    )
                } catch (_: Exception) {
                    // 경합 패자는 진행 중 결제를 보고 ORDER_NOT_PAYABLE — 정합성은 아래 최종 상태로 검증한다.
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 비관 락이 REQUESTED 생성을 직렬화: 첫 호출만 결제를 만들고 외부 요청, 나머지는 진행 중 결제를 보고 거른다.
        assertThat(requests.get()).isEqualTo(1)
        val requested = paymentRepository.findAllByStatus(PaymentStatus.REQUESTED)
        assertThat(requested).hasSize(1)
        assertThat(requested.single().orderId).isEqualTo(order.id)
        assertThat(requested.single().transactionId).isEqualTo("tx-${order.id}")
        // 결제 확정 전이므로 주문은 결제대기로 유지된다(승인은 콜백/폴링이 한다).
        assertThat(orderJpaRepository.findById(order.id).get().status).isEqualTo(OrderStatus.PAYMENT_PENDING)
    }
}
