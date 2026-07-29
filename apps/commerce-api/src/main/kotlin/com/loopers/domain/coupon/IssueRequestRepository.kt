package com.loopers.domain.coupon

interface IssueRequestRepository {
    fun save(issueRequest: IssueRequest): IssueRequest

    fun findByRequestId(requestId: String): IssueRequest?
}
