package com.loopers.application.ranking.result

/**
 * 랭킹 한 줄 — 순위·점수에 상품 정보를 조립한 결과. 상품 ID 만이 아니라 목록에 바로 쓸 정보를 담는다.
 */
data class RankedProductResult(
    val productId: Long,
    val name: String,
    val price: Long,
    val brandName: String,
    val likeCount: Long,
    val rank: Long,
    val score: Double,
)
