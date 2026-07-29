package com.loopers.domain.coupon

/**
 * 선착순 발급 요청의 거절 사유. HTTP 에러가 아니라 요청 결과 값으로, 결과 조회 응답에 그대로 노출된다.
 */
enum class RejectReason {
    SOLD_OUT,
    ALREADY_ISSUED,
}
