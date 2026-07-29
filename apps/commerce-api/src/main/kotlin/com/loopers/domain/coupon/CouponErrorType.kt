package com.loopers.domain.coupon

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class CouponErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    COUPON_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "COUPON_BAD_REQUEST", "쿠폰 입력이 올바르지 않습니다."),
    COUPON_NOT_APPLICABLE(ErrorStatus.BAD_REQUEST, "COUPON_NOT_APPLICABLE", "사용할 수 없는 쿠폰입니다."),
    COUPON_NOT_FOUND(ErrorStatus.NOT_FOUND, "COUPON_NOT_FOUND", "쿠폰을 찾을 수 없습니다."),
    USER_COUPON_NOT_FOUND(ErrorStatus.NOT_FOUND, "USER_COUPON_NOT_FOUND", "보유한 쿠폰을 찾을 수 없습니다."),
    ISSUE_REQUEST_NOT_FOUND(ErrorStatus.NOT_FOUND, "ISSUE_REQUEST_NOT_FOUND", "발급 요청을 찾을 수 없습니다."),
    ALREADY_ISSUED_COUPON(ErrorStatus.CONFLICT, "ALREADY_ISSUED_COUPON", "이미 발급받은 쿠폰입니다."),
    ALREADY_USED_COUPON(ErrorStatus.CONFLICT, "ALREADY_USED_COUPON", "이미 사용된 쿠폰입니다."),
    COUPON_SOLD_OUT(ErrorStatus.CONFLICT, "COUPON_SOLD_OUT", "발급 한도가 소진된 쿠폰입니다."),
}
