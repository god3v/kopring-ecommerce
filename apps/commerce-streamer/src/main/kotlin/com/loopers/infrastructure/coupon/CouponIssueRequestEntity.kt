package com.loopers.infrastructure.coupon

import com.loopers.domain.BaseEntity
import com.loopers.domain.coupon.IssueRequestRecord
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * `coupon_issue_request` 테이블을 commerce-api 와 공유해 매핑한다. streamer 는 이 행을 읽어 처리 결과를 확정(update) 한다.
 * 상태 전이 규칙은 도메인 모델(`IssueRequestRecord`) 이 가지고, 엔티티는 매핑만 한다.
 */
@Entity
@Table(name = "coupon_issue_request")
class CouponIssueRequestEntity : BaseEntity() {
    @Column(name = "request_id", nullable = false, unique = true, updatable = false, length = 36)
    var requestId: String = ""
        protected set

    @Column(name = "user_id", nullable = false, updatable = false)
    var userId: Long = 0L
        protected set

    @Column(name = "coupon_id", nullable = false, updatable = false)
    var couponId: Long = 0L
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: IssueRequestStatus = IssueRequestStatus.REQUESTED
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "reject_reason")
    var rejectReason: RejectReason? = null
        protected set

    @Column(name = "issued_user_coupon_id")
    var issuedUserCouponId: Long? = null
        protected set

    @Column(name = "requested_at", nullable = false, updatable = false)
    var requestedAt: LocalDateTime = LocalDateTime.MIN
        protected set

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null
        protected set

    fun toModel(): IssueRequestRecord = IssueRequestRecord.restore(
        requestId = requestId,
        userId = userId,
        couponId = couponId,
        status = status,
        rejectReason = rejectReason,
        issuedUserCouponId = issuedUserCouponId,
        requestedAt = requestedAt,
        processedAt = processedAt,
    )

    fun syncFrom(record: IssueRequestRecord) {
        status = record.status
        rejectReason = record.rejectReason
        issuedUserCouponId = record.issuedUserCouponId
        processedAt = record.processedAt
    }

    companion object {
        /** 접수 레코드를 새로 만든다. 실제 시스템에선 commerce-api 가 적재하고, streamer 통합 테스트가 시드로 쓴다. */
        fun create(requestId: String, userId: Long, couponId: Long, requestedAt: LocalDateTime): CouponIssueRequestEntity =
            CouponIssueRequestEntity().apply {
                this.requestId = requestId
                this.userId = userId
                this.couponId = couponId
                this.status = IssueRequestStatus.REQUESTED
                this.requestedAt = requestedAt
            }
    }
}
