package com.loopers.domain.ranking

/**
 * 랭킹판 한 항목 — 상품과 그 점수. 재구축의 입력으로 쓴다.
 */
data class RankedEntry(
    val productId: Long,
    val score: Double,
)
