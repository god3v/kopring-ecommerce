package com.loopers.domain.metrics

/**
 * 한 기간의 상품별 신호 합계 — 시간 버킷들을 합산한 결과. 랭킹 재계산의 입력이 된다.
 */
data class ProductSignalSummary(
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val orderQuantity: Long,
)
