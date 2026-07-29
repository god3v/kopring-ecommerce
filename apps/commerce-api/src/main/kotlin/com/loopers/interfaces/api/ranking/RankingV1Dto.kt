package com.loopers.interfaces.api.ranking

import com.loopers.application.ranking.result.RankedProductResult
import com.loopers.support.page.PageResult

class RankingV1Dto {
    data class RankingsResponse(
        val content: List<RankingItemResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<RankedProductResult>): RankingsResponse = RankingsResponse(
                content = page.content.map { RankingItemResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class RankingItemResponse(
        val rank: Long,
        val score: Double,
        val productId: Long,
        val name: String,
        val price: Long,
        val brandName: String,
        val likeCount: Long,
    ) {
        companion object {
            fun from(result: RankedProductResult): RankingItemResponse = RankingItemResponse(
                rank = result.rank,
                score = result.score,
                productId = result.productId,
                name = result.name,
                price = result.price,
                brandName = result.brandName,
                likeCount = result.likeCount,
            )
        }
    }
}
