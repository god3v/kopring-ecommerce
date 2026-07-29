package com.loopers.application.payment

import com.loopers.application.order.OrderCompensator
import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.CardNumber
import com.loopers.domain.payment.CardType
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentErrorType
import com.loopers.domain.payment.PaymentRepository
import com.loopers.support.error.CoreException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class PaymentFacade(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val orderCompensator: OrderCompensator,
    private val eventPublisher: DomainEventPublisher,
) {
    private val log = LoggerFactory.getLogger(PaymentFacade::class.java)

    @Transactional
    fun pay(command: PaymentCommand): PaymentRequestResult {
        // 카드 입력 검증(400) — 외부 호출·DB 조회 전에 먼저 거른다.
        val cardType = CardType.from(command.cardType)
        val cardNo = CardNumber(command.cardNo)

        val order = orderRepository.findByIdForUpdate(command.orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        // 본인 주문인지 검증
        order.validateOwnedBy(command.userId.toLong())
        // 이미 결제 진행/완료인지 검증
        order.validatePayable()
        // 결제 진행 상태 변경
        order.markPaymentPending()
        orderRepository.save(order)

        // 외부 호출 전에 결제를 먼저 영속한다 — 호출이 실패해도 폴링 복구 대상으로 남긴다.
        var payment = paymentRepository.save(Payment.request(orderId = order.id, amount = order.totalAmount))
        try {
            // 외부 PG 에 결제 요청 → 접수(거래 식별자 + 처리 중). 접수된 거래 식별자를 결제에 기록한다.
            val acceptance = paymentGateway.request(
                PaymentRequestCommand(
                    userId = command.userId,
                    orderId = order.id,
                    amount = order.totalAmount,
                    cardType = cardType.name,
                    cardNo = cardNo.value,
                    callbackUrl = CALLBACK_URL,
                ),
            )
            payment.accept(acceptance.transactionKey)
            payment = paymentRepository.save(payment)
        } catch (e: PaymentGatewayException) {
            // Fallback(일시 장애: 타임아웃·통신 실패·회로 차단) — 거래 식별자 미확보로 REQUESTED 유지, 폴링으로 복구한다.
            log.warn("PG 결제 요청 일시 실패 — 결제 {} 를 REQUESTED 로 유지(폴링 복구 대상): {}", payment.id, e.message)
        }
        return PaymentRequestResult(
            paymentId = payment.id,
            orderId = order.id,
            status = payment.status,
            transactionKey = payment.transactionId,
            amount = payment.amount,
            requestedAt = payment.requestedAt,
        )
    }

    /** 정산 — 콜백·폴링이 전달한 거래 결과를 거래 식별자로 매칭해 결제·주문에 반영한다. */
    @Transactional
    fun settle(transaction: PgTransaction) {
        // 거래 식별자로 결제를 비관 락 조회 — 콜백·폴링 동시 도착을 직렬화한다.
        val payment = paymentRepository.findByTransactionIdForUpdate(transaction.transactionKey) ?: return
        if (!payment.isRequested()) return // 이미 정산(승인·실패·취소)됨 — 중복 결과는 멱등하게 무시한다
        val order = orderRepository.findByIdForUpdate(payment.orderId) ?: return

        when (transaction.status) {
            PgTransactionStatus.SUCCESS -> {
                payment.approve(transaction.transactionKey, LocalDateTime.now())
                order.markPaid(transaction.transactionKey, transaction.status.name)
                // 결제 확정 사실 — outbox 를 타고 외부(판매 집계·랭킹)로 전파된다. settle 멱등 가드로 주문당 1회.
                eventPublisher.publish(OrderEvent.Paid.from(order))
            }
            PgTransactionStatus.FAILED -> {
                payment.fail(transaction.reason)
                orderCompensator.restore(order)
                order.markPaymentFailed(transaction.transactionKey, transaction.reason)
            }
            PgTransactionStatus.PENDING -> return // 아직 처리 중 — 미확정으로 둔다(후속 item)
        }
        paymentRepository.save(payment)
        orderRepository.save(order)
    }

    /**
     * 수동 복구 — 처리 중(REQUESTED) 결제의 외부 상태를 조회해 확정이면 정산한다.
     * 거래 식별자가 없으면(타임아웃 미확인) 주문 식별자로 외부 건을 찾아 식별자를 접수한 뒤 정산한다.
     */
    @Transactional
    fun sync(paymentId: Long): PaymentSyncResult {
        val payment = paymentRepository.findById(paymentId)
            ?: throw CoreException(PaymentErrorType.PAYMENT_NOT_FOUND)
        if (!payment.isRequested()) {
            return PaymentSyncResult(paymentId, payment.status, settled = false) // 이미 확정 — 변화 없음
        }
        val order = orderRepository.findById(payment.orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        val userId = order.userId.toString()

        // 거래 식별자가 있으면 그것으로, 없으면 주문 식별자로 외부 확정 거래를 찾는다(타임아웃 성공 수렴).
        // 외부 조회가 실패(통신 오류·회로 차단)하면 상태를 바꾸지 않고 미확정으로 둔다.
        val transaction = try {
            payment.transactionId
                ?.let { paymentGateway.getTransaction(userId, it) }
                ?: paymentGateway.getByOrder(userId, order.id).firstOrNull { it.status != PgTransactionStatus.PENDING }
        } catch (e: PaymentGatewayException) {
            log.warn("PG 상태 조회 실패 — 결제 {} 미확정 유지: {}", paymentId, e.message)
            null
        }

        if (transaction == null || transaction.status == PgTransactionStatus.PENDING) {
            return PaymentSyncResult(paymentId, payment.status, settled = false) // 외부 미확정
        }
        // 식별자 미확보였다면 발견한 거래 식별자를 접수 기록한다 — settle 이 거래 식별자로 결제를 매칭한다.
        if (!payment.hasTransactionId()) {
            payment.accept(transaction.transactionKey)
            paymentRepository.save(payment)
        }
        settle(transaction)
        val settled = paymentRepository.findById(paymentId)!!
        return PaymentSyncResult(paymentId, settled.status, settled = true)
    }

    companion object {
        // TODO: 환경별 외부화(payment.callback-url). pg-simulator 는 callbackUrl 이 http://localhost:8080 으로 시작할 것을 요구한다.
        private const val CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback"
    }
}
