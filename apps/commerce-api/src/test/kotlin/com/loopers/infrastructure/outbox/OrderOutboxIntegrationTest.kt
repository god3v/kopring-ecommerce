package com.loopers.infrastructure.outbox

import com.loopers.application.order.OrderFacade
import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.result.OrderResult
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate

/**
 * Transactional Outbox 기록 — 결제 성공 정산 커밋 시 `OrderEvent.Paid` 가 같은 트랜잭션에서 `outbox` 행으로 적재되는지 검증한다.
 * 주문 생성(`OrderEvent.Created`)은 내부 이벤트라 outbox 에 적재되지 않는다 — 판매 사실은 결제 확정 기준.
 * (Kafka 발행은 릴레이 증분에서, 여기서는 dual-write 를 없앤 "DB write + outbox 기록 원자성" 만 확인)
 */
@SpringBootTest
class OrderOutboxIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val paymentFacade: PaymentFacade,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val transactionTemplate: TransactionTemplate,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun placeOrder(idempotencyKey: String): OrderResult {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(price = 1000, stock = 10))
        return orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = idempotencyKey,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )
    }

    @Test
    fun `주문 생성 커밋은 outbox 에 적재되지 않는다 - ORDER_CREATED 는 내부 이벤트다`() {
        placeOrder("outbox-order-1")

        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)).isEmpty()
    }

    @Test
    fun `결제 성공 정산이 커밋되면 outbox 에 ORDER_PAID 행이 PENDING 으로 같은 트랜잭션에 기록된다`() {
        val result = placeOrder("outbox-order-2")
        // 결제 진행 상태의 주문 + 접수된 결제를 만든다 — 외부 PG 호출 없이 정산만 검증한다.
        transactionTemplate.execute {
            val order = orderRepository.findByIdForUpdate(result.orderId)!!
            order.markPaymentPending()
            orderRepository.save(order)
            paymentRepository.save(
                Payment.request(orderId = result.orderId, amount = result.totalAmount).also { it.accept("tx-outbox-1") },
            )
        }

        paymentFacade.settle(PgTransaction("tx-outbox-1", PgTransactionStatus.SUCCESS, null))

        val rows = outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].aggregateType).isEqualTo("ORDER")
        assertThat(rows[0].aggregateId).isEqualTo(result.orderId.toString())
        assertThat(rows[0].eventType).isEqualTo("ORDER_PAID")
        assertThat(rows[0].payload).contains("\"userId\":${result.userId}")
        assertThat(rows[0].payload).contains("\"lines\"")
        assertThat(rows[0].status).isEqualTo(OutboxStatus.PENDING)
        assertThat(rows[0].occurredAt).isNotNull()
        assertThat(rows[0].publishedAt).isNull()
    }
}
