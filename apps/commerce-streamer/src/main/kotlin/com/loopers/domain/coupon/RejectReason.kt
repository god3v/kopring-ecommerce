package com.loopers.domain.coupon

/**
 * 선착순 발급 거절 사유. commerce-api 와 같은 `coupon_issue_request.reject_reason` 컬럼을 공유하므로 enum 이름이 일치해야 한다.
 */
enum class RejectReason {
    SOLD_OUT,
    ALREADY_ISSUED,
}
