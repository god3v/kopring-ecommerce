package com.loopers.application.payment

/**
 * 결제 요청 유스케이스 입력 — 인증 회원·주문·카드. (포트로 PG 에 전달되는 `port.PaymentRequestCommand` 와 구분된다.)
 * 카드 번호는 외부 PG 로만 전달되고 우리 시스템에 저장·로깅하지 않는다.
 */
data class PaymentCommand(
    val userId: String,
    val orderId: Long,
    val cardType: String,
    val cardNo: String,
)
