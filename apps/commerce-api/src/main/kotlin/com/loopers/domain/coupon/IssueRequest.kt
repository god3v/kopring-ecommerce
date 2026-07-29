package com.loopers.domain.coupon

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * 선착순 쿠폰 발급 요청. 회원의 요청을 즉시 접수해 결과 추적용 레코드로 남기며, 발급 쿠폰과는 별개다.
 * 접수됨(REQUESTED) 으로 시작하고, 뒤이은 처리에서 발급됨 또는 거절됨으로 확정된다.
 */
class IssueRequest internal constructor(
    val requestId: String,
    val userId: Long,
    val couponId: Long,
    status: IssueRequestStatus = IssueRequestStatus.REQUESTED,
    val requestedAt: LocalDateTime,
) {
    var status: IssueRequestStatus = status
        private set

    var issuedUserCouponId: Long? = null
        private set

    var rejectReason: RejectReason? = null
        private set

    var processedAt: LocalDateTime? = null
        private set

    fun confirmIssued(userCouponId: Long, at: LocalDateTime) {
        if (status != IssueRequestStatus.REQUESTED) return
        status = IssueRequestStatus.ISSUED
        issuedUserCouponId = userCouponId
        processedAt = at
    }

    fun reject(reason: RejectReason, at: LocalDateTime) {
        if (status != IssueRequestStatus.REQUESTED) return
        status = IssueRequestStatus.REJECTED
        rejectReason = reason
        processedAt = at
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")

        fun request(userId: Long, couponId: Long): IssueRequest = IssueRequest(
            requestId = UUID.randomUUID().toString(),
            userId = userId,
            couponId = couponId,
            status = IssueRequestStatus.REQUESTED,
            requestedAt = LocalDateTime.now(SEOUL),
        )

        fun restore(
            requestId: String,
            userId: Long,
            couponId: Long,
            status: IssueRequestStatus,
            rejectReason: RejectReason?,
            issuedUserCouponId: Long?,
            requestedAt: LocalDateTime,
            processedAt: LocalDateTime?,
        ): IssueRequest = IssueRequest(
            requestId = requestId,
            userId = userId,
            couponId = couponId,
            status = status,
            requestedAt = requestedAt,
        ).also {
            it.rejectReason = rejectReason
            it.issuedUserCouponId = issuedUserCouponId
            it.processedAt = processedAt
        }
    }
}
