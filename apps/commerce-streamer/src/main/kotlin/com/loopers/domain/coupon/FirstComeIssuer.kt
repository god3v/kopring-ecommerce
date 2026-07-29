package com.loopers.domain.coupon

import java.time.LocalDateTime

/**
 * 선착순 발급의 원자 연산 — "한도 안에서 이 회원에게 한 장 발급"을 한 번에 시도하고 결과를 알려준다.
 * 한도 소진·중복 판정과 발급 수 원자 소진·발급 쿠폰 생성은 구현(어댑터)이 숨긴다.
 */
interface FirstComeIssuer {
    fun issue(userId: Long, couponId: Long, at: LocalDateTime): IssueOutcome
}

sealed class IssueOutcome {
    /** 발급 성공 — 생성된 발급 쿠폰 식별자. */
    data class Issued(val userCouponId: Long) : IssueOutcome()

    /** 한도 소진으로 발급 실패. */
    data object SoldOut : IssueOutcome()

    /** 이미 발급받은 회원의 중복 요청. */
    data object Duplicate : IssueOutcome()
}
