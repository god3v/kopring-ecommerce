package com.loopers.domain.coupon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class IssueRequestTest {
    @Test
    fun `발급 요청을 생성하면 접수됨 상태와 요청 식별자를 가진다`() {
        val request = IssueRequest.request(userId = 1L, couponId = 7L)

        assertThat(request.status).isEqualTo(IssueRequestStatus.REQUESTED)
        assertThat(request.requestId).isNotBlank()
        assertThat(request.userId).isEqualTo(1L)
        assertThat(request.couponId).isEqualTo(7L)
    }

    @Test
    fun `접수됨 요청을 발급됨으로 확정하면 발급된 쿠폰 식별자와 발급됨 상태를 가진다`() {
        val request = IssueRequest.request(userId = 1L, couponId = 7L)
        val at = LocalDateTime.of(2026, 7, 2, 12, 0, 0)

        request.confirmIssued(userCouponId = 501L, at = at)

        assertThat(request.status).isEqualTo(IssueRequestStatus.ISSUED)
        assertThat(request.issuedUserCouponId).isEqualTo(501L)
        assertThat(request.processedAt).isEqualTo(at)
    }

    @Test
    fun `접수됨 요청을 거절하면 거절됨 상태와 거절 사유를 가진다`() {
        val request = IssueRequest.request(userId = 1L, couponId = 7L)
        val at = LocalDateTime.of(2026, 7, 2, 12, 0, 0)

        request.reject(reason = RejectReason.SOLD_OUT, at = at)

        assertThat(request.status).isEqualTo(IssueRequestStatus.REJECTED)
        assertThat(request.rejectReason).isEqualTo(RejectReason.SOLD_OUT)
        assertThat(request.processedAt).isEqualTo(at)
    }

    @Test
    fun `이미 발급됨으로 확정된 요청은 거절해도 발급됨 결과가 바뀌지 않는다`() {
        val request = IssueRequest.request(userId = 1L, couponId = 7L)
        val issuedAt = LocalDateTime.of(2026, 7, 2, 12, 0, 0)
        request.confirmIssued(userCouponId = 501L, at = issuedAt)

        request.reject(reason = RejectReason.SOLD_OUT, at = LocalDateTime.of(2026, 7, 2, 12, 0, 1))

        assertThat(request.status).isEqualTo(IssueRequestStatus.ISSUED)
        assertThat(request.issuedUserCouponId).isEqualTo(501L)
        assertThat(request.rejectReason).isNull()
        assertThat(request.processedAt).isEqualTo(issuedAt)
    }

    @Test
    fun `이미 거절된 요청은 발급 확정해도 거절 결과가 바뀌지 않는다`() {
        val request = IssueRequest.request(userId = 1L, couponId = 7L)
        val rejectedAt = LocalDateTime.of(2026, 7, 2, 12, 0, 0)
        request.reject(reason = RejectReason.ALREADY_ISSUED, at = rejectedAt)

        request.confirmIssued(userCouponId = 501L, at = LocalDateTime.of(2026, 7, 2, 12, 0, 1))

        assertThat(request.status).isEqualTo(IssueRequestStatus.REJECTED)
        assertThat(request.rejectReason).isEqualTo(RejectReason.ALREADY_ISSUED)
        assertThat(request.issuedUserCouponId).isNull()
        assertThat(request.processedAt).isEqualTo(rejectedAt)
    }
}
