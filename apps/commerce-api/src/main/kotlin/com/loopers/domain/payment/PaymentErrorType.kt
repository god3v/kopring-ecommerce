package com.loopers.domain.payment

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class PaymentErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    INVALID_PAYMENT_TRANSITION(ErrorStatus.CONFLICT, "INVALID_PAYMENT_TRANSITION", "허용되지 않는 결제 상태 전이입니다."),
    TRANSACTION_ID_CONFLICT(ErrorStatus.CONFLICT, "TRANSACTION_ID_CONFLICT", "이미 접수된 거래 식별자를 다른 값으로 덮어쓸 수 없습니다."),
    UNSUPPORTED_CARD_TYPE(ErrorStatus.BAD_REQUEST, "UNSUPPORTED_CARD_TYPE", "지원하지 않는 카드 종류입니다."),
    INVALID_CARD_NUMBER(ErrorStatus.BAD_REQUEST, "INVALID_CARD_NUMBER", "카드 번호 형식이 올바르지 않습니다."),
    PAYMENT_NOT_FOUND(ErrorStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "결제를 찾을 수 없습니다."),
}
