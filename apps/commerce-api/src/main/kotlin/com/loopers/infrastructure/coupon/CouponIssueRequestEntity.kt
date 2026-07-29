package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * 선착순 발급 요청 레코드. `request_id` 를 유니크로 두어 결과 조회의 키로 쓴다.
 * 상태는 접수됨에서 시작해 발급됨/거절됨으로 확정되며, `commerce-streamer` 의 처리가 그 확정을 기록한다.
 */
@Entity
@Table(name = "coupon_issue_request")
class CouponIssueRequestEntity private constructor(
    requestId: String,
    userId: Long,
    couponId: Long,
    status: IssueRequestStatus,
    rejectReason: RejectReason?,
    issuedUserCouponId: Long?,
    requestedAt: LocalDateTime,
    processedAt: LocalDateTime?,
) : BaseEntity() {
    @Column(name = "request_id", nullable = false, unique = true, updatable = false, length = 36)
    var requestId: String = requestId
        protected set

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = userId
        protected set

    @Column(name = "coupon_id", nullable = false, updatable = false)
    var couponId: Long = couponId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: IssueRequestStatus = status
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason")
    var rejectReason: RejectReason? = rejectReason
        protected set

    @Column(name = "issued_user_coupon_id")
    var issuedUserCouponId: Long? = issuedUserCouponId
        protected set

    @Column(name = "requested_at", nullable = false, updatable = false)
    var requestedAt: LocalDateTime = requestedAt
        protected set

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = processedAt
        protected set

    fun toDomain(): IssueRequest = IssueRequest.restore(
        requestId = requestId,
        userId = userId,
        couponId = couponId,
        status = status,
        rejectReason = rejectReason,
        issuedUserCouponId = issuedUserCouponId,
        requestedAt = requestedAt,
        processedAt = processedAt,
    )

    companion object {
        fun from(request: IssueRequest): CouponIssueRequestEntity = CouponIssueRequestEntity(
            requestId = request.requestId,
            userId = request.userId,
            couponId = request.couponId,
            status = request.status,
            rejectReason = request.rejectReason,
            issuedUserCouponId = request.issuedUserCouponId,
            requestedAt = request.requestedAt,
            processedAt = request.processedAt,
        )
    }
}
