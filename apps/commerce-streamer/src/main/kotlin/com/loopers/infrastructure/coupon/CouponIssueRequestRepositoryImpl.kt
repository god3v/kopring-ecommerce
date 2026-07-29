package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.CouponIssueRequestRepository
import com.loopers.domain.coupon.IssueRequestRecord
import org.springframework.stereotype.Component

@Component
class CouponIssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : CouponIssueRequestRepository {
    override fun findByRequestId(requestId: String): IssueRequestRecord? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)?.toModel()

    override fun findByRequestIdForUpdate(requestId: String): IssueRequestRecord? =
        couponIssueRequestJpaRepository.findByRequestIdForUpdate(requestId)?.toModel()

    override fun save(record: IssueRequestRecord): IssueRequestRecord {
        // streamer 는 요청 행을 새로 만들지 않고, 접수된 행의 결과만 갱신한다.
        val entity = couponIssueRequestJpaRepository.findByRequestId(record.requestId)
            ?: throw IllegalStateException("issue request not found: ${record.requestId}")
        entity.syncFrom(record)
        return couponIssueRequestJpaRepository.save(entity).toModel()
    }
}
