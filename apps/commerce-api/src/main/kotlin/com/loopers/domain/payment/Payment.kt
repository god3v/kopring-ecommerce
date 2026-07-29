package com.loopers.domain.payment

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Payment internal constructor(
    val id: Long = 0L,
    val orderId: Long,
    val amount: Long,
    status: PaymentStatus = PaymentStatus.REQUESTED,
    transactionId: String? = null,
    failureReason: String? = null,
    val requestedAt: LocalDateTime,
    paidAt: LocalDateTime? = null,
    canceledAt: LocalDateTime? = null,
) {
    var status: PaymentStatus = status
        private set

    var transactionId: String? = transactionId
        private set

    var failureReason: String? = failureReason
        private set

    var paidAt: LocalDateTime? = paidAt
        private set

    var canceledAt: LocalDateTime? = canceledAt
        private set

    /**
     * PG 요청 접수 반영 — 외부 거래 식별자를 기록한다(결과 확정 전, 폴링·콜백 매칭용).
     * 한 번 접수한 식별자는 다른 값으로 덮어쓰지 않는다 — 같은 값 재접수는 멱등 통과(중복 요청 방어).
     */
    fun accept(transactionId: String) {
        val current = this.transactionId
        if (!current.isNullOrBlank() && current != transactionId) {
            throw CoreException(PaymentErrorType.TRANSACTION_ID_CONFLICT)
        }
        this.transactionId = transactionId
    }

    /** PG 승인 반영 — REQUESTED 에서만 가능하다. 외부 거래 식별자와 결제 시각을 기록한다. */
    fun approve(transactionId: String, at: LocalDateTime) {
        if (status != PaymentStatus.REQUESTED) {
            throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "요청 상태의 결제만 승인할 수 있다.")
        }
        status = PaymentStatus.APPROVED
        this.transactionId = transactionId
        paidAt = at
    }

    /** PG 거절 반영 — 멱등(이미 FAILED 면 no-op). 승인·취소된 결제는 실패로 뒤집을 수 없다. */
    fun fail(failureReason: String?) {
        when (status) {
            PaymentStatus.FAILED -> return
            PaymentStatus.REQUESTED -> {
                status = PaymentStatus.FAILED
                this.failureReason = failureReason
            }
            PaymentStatus.APPROVED, PaymentStatus.CANCELED ->
                throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "승인·취소된 결제는 실패로 전이할 수 없다.")
        }
    }

    /** 취소 — REQUESTED(청구 전, 환불 불필요)·APPROVED(환불) 에서 가능하다. 멱등. */
    fun cancel(at: LocalDateTime) {
        when (status) {
            PaymentStatus.CANCELED -> return
            PaymentStatus.REQUESTED, PaymentStatus.APPROVED -> {
                status = PaymentStatus.CANCELED
                canceledAt = at
            }
            PaymentStatus.FAILED ->
                throw CoreException(PaymentErrorType.INVALID_PAYMENT_TRANSITION, "실패한 결제는 취소할 수 없다.")
        }
    }

    fun isRequested(): Boolean = status == PaymentStatus.REQUESTED

    fun isApproved(): Boolean = status == PaymentStatus.APPROVED

    /** 외부 거래 식별자 확보 여부 — 복구 시 거래 식별자 조회(true) vs 주문 식별자 조회(false) 를 가른다. */
    fun hasTransactionId(): Boolean = !transactionId.isNullOrBlank()

    companion object {
        fun request(orderId: Long, amount: Long): Payment = Payment(
            orderId = orderId,
            amount = amount,
            status = PaymentStatus.REQUESTED,
            requestedAt = LocalDateTime.now(),
        )
    }
}
