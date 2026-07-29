package com.loopers.application.order

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentFacade
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.infrastructure.payment.PaymentApiClient
import com.loopers.infrastructure.payment.PgSimulatorPaymentGateway
import com.loopers.support.error.CoreException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.LocalDateTime

@DisplayName("PaymentFacade — 결제 요청(pay)·정산(settle)")
class PaymentFacadeTest {
    private val orderRepository: OrderRepository = mockk(relaxed = true)
    private val paymentRepository: PaymentRepository = mockk()
    private val paymentGateway: PaymentGateway = mockk(relaxed = true)
    private val orderCompensator: OrderCompensator = mockk(relaxed = true)
    private val eventPublisher: DomainEventPublisher = mockk(relaxed = true)
    private val paymentFacade = PaymentFacade(orderRepository, paymentRepository, paymentGateway, orderCompensator, eventPublisher)

    private fun createdOrder() = Order(
        id = 1L,
        userId = 1L,
        lines = listOf(OrderLine.create(productId = 1L, productName = "운동화", unitPrice = 1000, quantity = 2)),
        orderedAt = LocalDateTime.now(),
        idempotencyKey = "k",
    )

    // 주문 소유자(userId=1L)와 같은 회원이 결제를 요청하는 정상 케이스.
    private fun command() =
        PaymentCommand(userId = "1", orderId = 1L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451")

    @DisplayName("결제대기 주문에 결제를 요청하면 주문을 결제 진행으로 전이하고, 발급된 거래 식별자를 기록해 접수 정보를 반환한다.")
    @Test
    fun marksPendingRecordsTransactionKeyAndReturnsAcceptance() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentGateway.request(any()) } returns PaymentResponse("tx-key-1", PgTransactionStatus.PENDING)
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-key-1", requestedAt = LocalDateTime.now())

        val info = paymentFacade.pay(command())

        assertThat(info.paymentId).isEqualTo(10L)
        assertThat(info.orderId).isEqualTo(1L)
        assertThat(info.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(info.transactionKey).isEqualTo("tx-key-1")
        assertThat(info.amount).isEqualTo(2000L)
        verify { paymentGateway.request(any()) }
    }

    @DisplayName("외부 PG 가 일시 장애(PaymentGatewayException)면 결제를 REQUESTED(거래 식별자 없음)로 두고 접수 응답을 반환한다 — 예외를 전파하지 않는다.")
    @Test
    fun fallsBackToRequestedOnGatewayFailure() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentGateway.request(any()) } throws PaymentGatewayException("타임아웃")
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, requestedAt = LocalDateTime.now())

        val info = paymentFacade.pay(command())

        assertThat(info.paymentId).isEqualTo(10L)
        assertThat(info.status).isEqualTo(PaymentStatus.REQUESTED)
        assertThat(info.transactionKey).isNull()
        verify { paymentGateway.request(any()) }
    }

    @DisplayName("외부 PG 가 계속 실패하면 최대 시도까지 재시도한 뒤 멈추고, 결제를 REQUESTED 로 두는 Fallback 으로 이어진다.")
    @Test
    fun exhaustsRetriesThenFallsBack() {
        // 항상 일시 장애를 던지는 스텁 전송 클라이언트 — 실제 Retry 가 몇 번 호출하는지 센다.
        var calls = 0
        val failingClient = object : PaymentApiClient {
            override fun requestPayment(command: PaymentRequestCommand): PaymentResponse {
                calls++
                throw PaymentGatewayException("타임아웃")
            }

            override fun getTransaction(userId: String, transactionKey: String): PgTransaction =
                throw UnsupportedOperationException()

            override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> =
                throw UnsupportedOperationException()
        }
        // 실제 Retry(최대 3회) 를 입힌 게이트웨이 + 실제 facade 로 "소진 → Fallback" 통합을 검증한다.
        val retry = Retry.of(
            "test",
            RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(PaymentGatewayException::class.java)
                .build(),
        )
        val gateway = PgSimulatorPaymentGateway(failingClient, CircuitBreaker.ofDefaults("test"), retry)
        val facade = PaymentFacade(orderRepository, paymentRepository, gateway, orderCompensator, eventPublisher)
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, requestedAt = LocalDateTime.now())

        val info = facade.pay(command())

        assertThat(calls).isEqualTo(3) // 최대 시도(3)까지 재시도하고 멈춤
        assertThat(info.status).isEqualTo(PaymentStatus.REQUESTED) // Fallback — 예외 전파 없음
        assertThat(info.transactionKey).isNull()
    }

    @DisplayName("재시도가 성공으로 회복돼도 결제는 한 건만 접수된다 — 재시도로 인한 이중 결제가 없다.")
    @Test
    fun retrySuccessYieldsSingleAcceptance() {
        // 첫 시도는 일시 실패, 두 번째 시도는 성공하는 스텁 — 재시도로 회복되는 경로.
        var calls = 0
        val flakyClient = object : PaymentApiClient {
            override fun requestPayment(command: PaymentRequestCommand): PaymentResponse {
                calls++
                if (calls == 1) throw PaymentGatewayException("일시 실패")
                return PaymentResponse("tx-recovered", PgTransactionStatus.PENDING)
            }

            override fun getTransaction(userId: String, transactionKey: String): PgTransaction =
                throw UnsupportedOperationException()

            override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> =
                throw UnsupportedOperationException()
        }
        val retry = Retry.of(
            "test",
            RetryConfig.custom<Any>()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(PaymentGatewayException::class.java)
                .build(),
        )
        val gateway = PgSimulatorPaymentGateway(flakyClient, CircuitBreaker.ofDefaults("test"), retry)
        val facade = PaymentFacade(orderRepository, paymentRepository, gateway, orderCompensator, eventPublisher)
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder()
        every { paymentRepository.save(any()) } returns
            Payment(id = 10L, orderId = 1L, amount = 2000L, requestedAt = LocalDateTime.now())

        val info = facade.pay(command())

        assertThat(calls).isEqualTo(2) // 1 실패 + 1 성공 — 재시도로 회복
        assertThat(info.paymentId).isEqualTo(10L) // 결제는 한 건만
        assertThat(info.transactionKey).isEqualTo("tx-recovered") // 거래 식별자도 하나만 기록
    }

    @DisplayName("이미 결제 진행/완료인 주문(결제 가능 상태 아님)에 결제를 요청하면 ORDER_NOT_PAYABLE 이 발생하고 외부 PG 를 호출하지 않는다.")
    @Test
    fun rejectsWhenOrderNotPayable() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder().also { it.markPaymentPending() }

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("타인 소유 주문에 결제를 요청하면 ORDER_FORBIDDEN 이 발생하고 외부 PG 를 호출하지 않는다.")
    @Test
    fun rejectsWhenOrderOwnedByAnother() {
        every { orderRepository.findByIdForUpdate(1L) } returns createdOrder() // 주문 소유자 = 1L

        val ex = assertThrows<CoreException> {
            paymentFacade.pay(
                PaymentCommand(userId = "999", orderId = 1L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"),
            )
        }

        assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_FORBIDDEN)
        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("주문이 존재하지 않으면 ORDER_NOT_FOUND 가 발생한다.")
    @Test
    fun rejectsWhenOrderMissing() {
        every { orderRepository.findByIdForUpdate(1L) } returns null

        assertThrows<CoreException> { paymentFacade.pay(command()) }

        verify(exactly = 0) { paymentGateway.request(any()) }
    }

    @DisplayName("정산(settle) 은, ")
    @Nested
    inner class Settle {
        private fun pendingOrder() = createdOrder().also { it.markPaymentPending() }

        @DisplayName("외부 결과가 성공이면 결제는 APPROVED, 주문은 PAID 로 전이한다.")
        @Test
        fun approvesAndMarksPaidOnSuccess() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-1", requestedAt = LocalDateTime.now())
            val order = pendingOrder()
            every { paymentRepository.findByTransactionIdForUpdate("tx-1") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-1", PgTransactionStatus.SUCCESS, null))

            assertThat(payment.status).isEqualTo(PaymentStatus.APPROVED)
            assertThat(payment.transactionId).isEqualTo("tx-1")
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }

        @DisplayName("외부 결과가 성공이면 OrderEvent.Paid 를 정확히 1회 발행한다 — 중복 발행되면 판매 집계·랭킹이 같은 결제를 두 번 반영한다.")
        @Test
        fun publishesOrderPaidExactlyOnceOnSuccess() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-1", requestedAt = LocalDateTime.now())
            val order = pendingOrder()
            every { paymentRepository.findByTransactionIdForUpdate("tx-1") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-1", PgTransactionStatus.SUCCESS, null))

            // 내용이 다른 Paid 가 섞여 나가는 회귀까지 잡기 위해, 타입 기준 1회를 먼저 못 박는다.
            verify(exactly = 1) { eventPublisher.publish(ofType(OrderEvent.Paid::class)) }
            verify(exactly = 1) {
                eventPublisher.publish(
                    match {
                        it is OrderEvent.Paid &&
                            it.orderId == 1L &&
                            it.userId == 1L &&
                            it.totalAmount == 2000L &&
                            it.lines.map { line -> line.productId to line.quantity }.toSet() == setOf(1L to 2)
                    },
                )
            }
        }

        @DisplayName("외부 결과가 실패면 OrderEvent.Paid 를 발행하지 않는다 — 결제 확정 사실이 아니다.")
        @Test
        fun doesNotPublishPaidOnFailure() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-f", requestedAt = LocalDateTime.now())
            every { paymentRepository.findByTransactionIdForUpdate("tx-f") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns pendingOrder()
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-f", PgTransactionStatus.FAILED, "DECLINED"))

            verify(exactly = 0) { eventPublisher.publish(any()) }
        }

        @DisplayName("외부 결과가 실패면 결제는 FAILED, 주문은 PAYMENT_FAILED 로 전이하고 보상(재고·쿠폰 원복)을 수행한다.")
        @Test
        fun failsCompensatesAndMarksFailedOnFailure() {
            val payment = Payment(id = 10L, orderId = 1L, amount = 2000L, transactionId = "tx-f", requestedAt = LocalDateTime.now())
            val order = pendingOrder()
            every { paymentRepository.findByTransactionIdForUpdate("tx-f") } returns payment
            every { orderRepository.findByIdForUpdate(1L) } returns order
            every { paymentRepository.save(any()) } answers { firstArg() }
            every { orderRepository.save(any()) } answers { firstArg() }

            paymentFacade.settle(PgTransaction("tx-f", PgTransactionStatus.FAILED, "DECLINED"))

            assertThat(payment.status).isEqualTo(PaymentStatus.FAILED)
            assertThat(payment.failureReason).isEqualTo("DECLINED")
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            verify { orderCompensator.restore(order) }
        }

        @DisplayName("이미 정산된(REQUESTED 가 아닌) 결제에 결과를 다시 반영하면 멱등하게 무시한다 — 주문 조회·저장·보상이 일어나지 않는다.")
        @Test
        fun ignoresWhenAlreadySettled() {
            val payment = Payment(
                id = 10L,
                orderId = 1L,
                amount = 2000L,
                status = PaymentStatus.APPROVED,
                transactionId = "tx-1",
                requestedAt = LocalDateTime.now(),
            )
            every { paymentRepository.findByTransactionIdForUpdate("tx-1") } returns payment

            paymentFacade.settle(PgTransaction("tx-1", PgTransactionStatus.SUCCESS, null))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { orderCompensator.restore(any()) }
            verify(exactly = 0) { eventPublisher.publish(any()) } // 중복 통지에 Paid 가 다시 나가지 않는다
        }

        @DisplayName("알 수 없는 거래 식별자의 결과 통지는 정산 없이 무시한다 — 주문 조회·저장·보상이 일어나지 않는다.")
        @Test
        fun ignoresUnknownTransaction() {
            every { paymentRepository.findByTransactionIdForUpdate("unknown") } returns null

            paymentFacade.settle(PgTransaction("unknown", PgTransactionStatus.SUCCESS, null))

            verify(exactly = 0) { orderRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { paymentRepository.save(any()) }
            verify(exactly = 0) { orderCompensator.restore(any()) }
        }
    }
}
