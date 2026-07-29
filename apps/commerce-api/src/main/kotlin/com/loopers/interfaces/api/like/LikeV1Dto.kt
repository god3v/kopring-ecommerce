package com.loopers.interfaces.api.like

import com.loopers.application.like.result.LikedProductResult
import com.loopers.support.page.PageResult

class LikeV1Dto {
    data class LikedProductsResponse(
        val content: List<LikedProductItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<LikedProductResult>): LikedProductsResponse = LikedProductsResponse(
                content = page.content.map { LikedProductItem.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class LikedProductItem(
        val productId: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brandId: Long,
    ) {
        companion object {
            fun from(result: LikedProductResult): LikedProductItem = LikedProductItem(
                productId = result.productId,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
            )
        }
    }
}
