package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentCommand
import com.loopers.application.payment.PaymentRequestResult
import com.loopers.application.payment.PaymentSyncResult
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.payment.PaymentStatus
import java.time.LocalDateTime

/**
 * 결제 API DTO. `status` 는 도메인 `PaymentStatus` 이름을 그대로 직렬화한다.
 * 카드 번호는 요청으로만 받아 외부 PG 로만 전달하고, 어떤 응답에도 노출하지 않는다.
 */
class PaymentV1Dto {
    data class PayRequest(
        val orderId: Long,
        val cardType: String,
        val cardNo: String,
    ) {
        fun toCommand(userId: String): PaymentCommand = PaymentCommand(
            userId = userId,
            orderId = orderId,
            cardType = cardType,
            cardNo = cardNo,
        )
    }

    /**
     * 외부 PG 결제 결과 콜백 본문. `transactionKey` 로 정산 대상 결제를 매칭한다.
     * `cardType`·`cardNo`·`amount` 는 수신만 하고 사용·저장·로깅하지 않는다.
     */
    data class CallbackRequest(
        val transactionKey: String,
        val status: PgTransactionStatus,
        val reason: String? = null,
        val orderId: String? = null,
        val cardType: String? = null,
        val cardNo: String? = null,
        val amount: Long? = null,
    ) {
        fun toTransaction(): PgTransaction = PgTransaction(transactionKey = transactionKey, status = status, reason = reason)
    }

    data class PayResponse(
        val paymentId: Long,
        val orderId: Long,
        val status: PaymentStatus,
        val transactionKey: String?,
        val amount: Long,
        val requestedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: PaymentRequestResult): PayResponse = PayResponse(
                paymentId = result.paymentId,
                orderId = result.orderId,
                status = result.status,
                transactionKey = result.transactionKey,
                amount = result.amount,
                requestedAt = result.requestedAt,
            )
        }
    }

    /** 어드민 수동 복구 응답 — 복구 후 결제 상태와 이번 호출로 정산이 일어났는지(`settled`). */
    data class SyncResponse(
        val paymentId: Long,
        val status: PaymentStatus,
        val settled: Boolean,
    ) {
        companion object {
            fun from(result: PaymentSyncResult): SyncResponse = SyncResponse(
                paymentId = result.paymentId,
                status = result.status,
                settled = result.settled,
            )
        }
    }
}
