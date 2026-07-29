package com.loopers.application.payment

import com.loopers.domain.payment.PaymentStatus
import java.time.LocalDateTime

/**
 * 결제 요청 접수 결과 — 회원에게 즉시 반환되는 처리 중 정보. 카드 번호는 포함하지 않는다.
 * 최종 결과(승인/실패)는 비동기(콜백/폴링)로 확정되며 이후 조회에서 드러난다.
 */
data class PaymentRequestResult(
    val paymentId: Long,
    val orderId: Long,
    val status: PaymentStatus,
    val transactionKey: String?,
    val amount: Long,
    val requestedAt: LocalDateTime,
)
