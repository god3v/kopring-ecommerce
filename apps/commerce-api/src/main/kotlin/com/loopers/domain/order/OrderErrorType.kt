package com.loopers.domain.order

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class OrderErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    EMPTY_LINES(ErrorStatus.BAD_REQUEST, "EMPTY_LINES", "주문에는 1개 이상의 라인이 필요합니다."),
    INVALID_QUANTITY(ErrorStatus.BAD_REQUEST, "INVALID_QUANTITY", "수량은 1 이상이어야 합니다."),
    LINE_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "LINE_BAD_REQUEST", "주문 라인 입력이 올바르지 않습니다."),
    IDEMPOTENCY_KEY_BLANK(ErrorStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_BLANK", "idempotencyKey 는 비어 있을 수 없습니다."),
    INVALID_DATE_RANGE(ErrorStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "startAt 은 endAt 보다 클 수 없습니다."),
    ORDER_NOT_FOUND(ErrorStatus.NOT_FOUND, "ORDER_NOT_FOUND", "주문을 찾을 수 없습니다."),
    ORDER_FORBIDDEN(ErrorStatus.FORBIDDEN, "ORDER_FORBIDDEN", "본인의 주문이 아닙니다."),
    ORDER_NOT_PAYABLE(ErrorStatus.CONFLICT, "ORDER_NOT_PAYABLE", "결제할 수 없는 주문 상태입니다."),
    INVALID_PAYMENT_TRANSITION(ErrorStatus.CONFLICT, "INVALID_PAYMENT_TRANSITION", "허용되지 않는 결제 상태 전이입니다."),
}
