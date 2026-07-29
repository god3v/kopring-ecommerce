package com.loopers.domain.coupon

import com.loopers.support.event.ExternalEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 선착순 발급 요청 접수 이벤트. 시스템 경계를 넘어 `commerce-streamer` 가 소비해 선착순 발급/거절을 처리한다.
 * `aggregateId=couponId` 로 같은 쿠폰의 요청을 한 파티션에 실어 순서(선착순) 기준을 만든다.
 * `requestId` 로 처리기가 어느 발급 요청 레코드를 확정할지 식별한다.
 */
data class CouponIssueRequestedEvent(
    val couponId: Long,
    val userId: Long,
    val requestId: String,
    override val eventId: UUID = UUID.randomUUID(),
    override val occurredAt: LocalDateTime = LocalDateTime.now(),
) : ExternalEvent {
    override val aggregateType: String get() = "COUPON_ISSUE_REQUEST"
    override val aggregateId: String get() = couponId.toString()
    override val eventType: String get() = "COUPON_ISSUE_REQUESTED"
}
