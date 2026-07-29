package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.stereotype.Component

/**
 * pg-simulator 연동 어댑터. HTTP 전송은 PaymentApiClient 에 위임하고, 회복 전략(회로 차단·재시도)만 책임진다.
 */
@Component
class PgSimulatorPaymentGateway(
    private val paymentApiClient: PaymentApiClient,
    private val pgCircuitBreaker: CircuitBreaker,
    private val pgRetry: Retry,
) : PaymentGateway {
    override fun request(request: PaymentRequestCommand): PaymentResponse =
        execute("결제 요청") { paymentApiClient.requestPayment(request) }

    override fun getTransaction(userId: String, transactionKey: String): PgTransaction =
        execute("결제 조회") { paymentApiClient.getTransaction(userId, transactionKey) }

    override fun getByOrder(userId: String, orderId: Long): List<PgTransaction> =
        execute("주문별 결제 조회") { paymentApiClient.getByOrder(userId, orderId) }

    /**
     * 전송 호출을 재시도(안쪽)·회로 차단기(바깥쪽)로 감싼다.
     * 재시도 묶음 전체가 차단기에 한 이벤트로 집계되고, 회로가 열려 있으면 재시도 전에 차단된다.
     */
    private fun <T> execute(action: String, block: () -> T): T {
        val retried = Retry.decorateSupplier(pgRetry) { block() }
        val guarded = CircuitBreaker.decorateSupplier(pgCircuitBreaker, retried)
        return try {
            guarded.get()
        } catch (e: CallNotPermittedException) {
            throw PaymentGatewayException("PG 회로 차단 — $action 차단됨", e)
        }
    }
}
