package com.loopers.application.coupon.result

import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import java.time.LocalDateTime

/**
 * 선착순 발급 요청 결과 조회 — 현재 상태와, 발급됨이면 발급된 쿠폰 식별자, 거절됨이면 거절 사유.
 */
data class FirstComeIssueResult(
    val requestId: String,
    val couponId: Long,
    val status: IssueRequestStatus,
    val rejectReason: RejectReason?,
    val issuedUserCouponId: Long?,
    val processedAt: LocalDateTime?,
) {
    companion object {
        fun of(request: IssueRequest): FirstComeIssueResult = FirstComeIssueResult(
            requestId = request.requestId,
            couponId = request.couponId,
            status = request.status,
            rejectReason = request.rejectReason,
            issuedUserCouponId = request.issuedUserCouponId,
            processedAt = request.processedAt,
        )
    }
}
