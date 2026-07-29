package com.loopers.domain.coupon

/**
 * 선착순 발급 요청 처리 상태. commerce-api 와 같은 `coupon_issue_request.status` 컬럼을 공유하므로 enum 이름이 일치해야 한다.
 */
enum class IssueRequestStatus {
    REQUESTED,
    ISSUED,
    REJECTED,
}
