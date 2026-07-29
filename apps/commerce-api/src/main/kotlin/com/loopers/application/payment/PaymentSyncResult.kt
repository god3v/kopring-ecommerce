package com.loopers.application.payment

import com.loopers.domain.payment.PaymentStatus

/**
 * 결제 수동 복구 결과 — 외부 상태를 조회해 확정 시 정산한 뒤의 결제 상태와, 이번 호출로 실제 정산이 일어났는지(`settled`).
 * 외부가 미확정(PENDING)이거나 이미 확정된 결제면 `settled = false` 로 상태 변화가 없다.
 */
data class PaymentSyncResult(
    val paymentId: Long,
    val status: PaymentStatus,
    val settled: Boolean,
)
