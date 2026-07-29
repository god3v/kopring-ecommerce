package com.loopers.domain.coupon

interface CouponIssueRequestRepository {
    fun findByRequestId(requestId: String): IssueRequestRecord?

    /**
     * 처리 경로 전용 비관적 쓰기 락 조회 — 리밸런스 등으로 같은 요청이 동시에 두 번 배달돼도
     * 행 락이 처리를 직렬화해, 뒤따르는 쪽은 확정된 상태를 보고 건너뛴다.
     */
    fun findByRequestIdForUpdate(requestId: String): IssueRequestRecord?

    fun save(record: IssueRequestRecord): IssueRequestRecord
}
