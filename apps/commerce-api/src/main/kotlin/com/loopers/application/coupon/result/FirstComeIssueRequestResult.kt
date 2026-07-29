package com.loopers.application.coupon.result

import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestStatus
import java.time.LocalDateTime

/**
 * 선착순 발급 요청 접수 결과 — 즉시 응답으로 돌려주는 요청 식별자와 접수 상태.
 */
data class FirstComeIssueRequestResult(
    val requestId: String,
    val couponId: Long,
    val status: IssueRequestStatus,
    val requestedAt: LocalDateTime,
) {
    companion object {
        fun of(request: IssueRequest): FirstComeIssueRequestResult = FirstComeIssueRequestResult(
            requestId = request.requestId,
            couponId = request.couponId,
            status = request.status,
            requestedAt = request.requestedAt,
        )
    }
}
