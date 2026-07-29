package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.FirstComeIssuer
import com.loopers.domain.coupon.IssueOutcome
import com.loopers.domain.coupon.RejectReason
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 선착순 발급 처리의 트랜잭션 경계·조율을 단일 소유한다.
 * 접수된 요청을 읽어 한 번만 처리(발급/거절) 하고 결과를 확정한다 — 이미 확정된 요청은 건드리지 않아 재소비가 멱등하다.
 * 발급 여부는 요청 접수 시 저장된 회원·쿠폰으로 판단한다(이벤트 payload 가 아니라 접수 레코드가 근거).
 */
@Component
class FirstComeIssueFacade(
    private val couponIssueRequestRepository: CouponIssueRequestRepository,
    private val firstComeIssuer: FirstComeIssuer,
) {
    @Transactional
    fun handle(requestId: String) {
        // 요청 행을 비관 락으로 잠근다 — 같은 요청이 동시에 두 번 배달돼도(리밸런스 재전달) 처리는 직렬화되고,
        // 뒤따르는 쪽은 확정된 상태를 보고 건너뛴다.
        val request = couponIssueRequestRepository.findByRequestIdForUpdate(requestId) ?: return
        if (!request.isPending()) return

        val now = LocalDateTime.now(SEOUL)
        when (val outcome = firstComeIssuer.issue(request.userId, request.couponId, now)) {
            is IssueOutcome.Issued -> request.markIssued(outcome.userCouponId, now)
            IssueOutcome.SoldOut -> request.markRejected(RejectReason.SOLD_OUT, now)
            IssueOutcome.Duplicate -> request.markRejected(RejectReason.ALREADY_ISSUED, now)
        }
        couponIssueRequestRepository.save(request)
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
