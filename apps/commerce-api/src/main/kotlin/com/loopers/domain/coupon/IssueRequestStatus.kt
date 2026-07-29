package com.loopers.domain.coupon

/**
 * 선착순 발급 요청의 처리 상태. 접수됨에서 시작해 발급됨 또는 거절됨으로 한 번만 확정된다.
 */
enum class IssueRequestStatus {
    REQUESTED,
    ISSUED,
    REJECTED,
}
