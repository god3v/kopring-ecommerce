package com.loopers.domain.metrics

/**
 * 시간 버킷을 창 단위로 합산한 상품별 신호 요약 — 기간 키가 붙기 전의 읽기 결과.
 */
data class ProductSignalSummary(
    val productId: Long,
    val viewCount: Long,
    val likeCount: Long,
    val orderQuantity: Long,
)
