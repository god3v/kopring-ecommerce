package com.loopers.application.like.result

import com.loopers.domain.product.Product

data class LikedProductResult(
    val productId: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
) {
    companion object {
        fun from(product: Product): LikedProductResult = LikedProductResult(
            productId = product.id,
            name = product.name.value,
            price = product.price.value,
            likeCount = product.likeCount,
            brandId = product.brandId,
        )
    }
}
