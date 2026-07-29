package com.loopers.domain.ranking

/**
 * 랭킹판의 한 줄 — 상품 식별자와 그 점수. 상품 정보 조립 전의 원시 조회 결과다.
 */
data class RankedEntry(val productId: Long, val score: Double)
