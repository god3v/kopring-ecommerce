package com.loopers.infrastructure.coupon

import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestRepository
import org.springframework.stereotype.Component

@Component
class IssueRequestRepositoryImpl(
    private val couponIssueRequestJpaRepository: CouponIssueRequestJpaRepository,
) : IssueRequestRepository {
    override fun save(issueRequest: IssueRequest): IssueRequest =
        couponIssueRequestJpaRepository.save(CouponIssueRequestEntity.from(issueRequest)).toDomain()

    override fun findByRequestId(requestId: String): IssueRequest? =
        couponIssueRequestJpaRepository.findByRequestId(requestId)?.toDomain()
}
