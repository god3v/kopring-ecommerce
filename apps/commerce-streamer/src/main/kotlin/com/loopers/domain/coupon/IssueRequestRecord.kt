package com.loopers.domain.coupon

import java.time.LocalDateTime

/**
 * 선착순 발급 요청의 처리 상태를 담는 도메인 모델. 접수됨(REQUESTED) 에서 발급됨/거절됨으로 한 번만 전이한다.
 * 이미 확정된 요청은 재확정해도 바뀌지 않아, 같은 메시지가 다시 도착해도 결과가 한 번만 반영된다(멱등).
 */
class IssueRequestRecord private constructor(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    status: IssueRequestStatus,
    rejectReason: RejectReason?,
    issuedUserCouponId: Long?,
    val requestedAt: LocalDateTime,
    processedAt: LocalDateTime?,
) {
    var status: IssueRequestStatus = status
        private set

    var rejectReason: RejectReason? = rejectReason
        private set

    var issuedUserCouponId: Long? = issuedUserCouponId
        private set

    var processedAt: LocalDateTime? = processedAt
        private set

    fun isPending(): Boolean = status == IssueRequestStatus.REQUESTED

    fun markIssued(userCouponId: Long, at: LocalDateTime) {
        if (!isPending()) return
        status = IssueRequestStatus.ISSUED
        issuedUserCouponId = userCouponId
        processedAt = at
    }

    fun markRejected(reason: RejectReason, at: LocalDateTime) {
        if (!isPending()) return
        status = IssueRequestStatus.REJECTED
        rejectReason = reason
        processedAt = at
    }

    companion object {
        fun restore(
            requestId: String,
            userId: Long,
            couponId: Long,
            status: IssueRequestStatus,
            rejectReason: RejectReason?,
            issuedUserCouponId: Long?,
            requestedAt: LocalDateTime,
            processedAt: LocalDateTime?,
        ): IssueRequestRecord = IssueRequestRecord(
            requestId = requestId,
            userId = userId,
            couponId = couponId,
            status = status,
            rejectReason = rejectReason,
            issuedUserCouponId = issuedUserCouponId,
            requestedAt = requestedAt,
            processedAt = processedAt,
        )
    }
}
