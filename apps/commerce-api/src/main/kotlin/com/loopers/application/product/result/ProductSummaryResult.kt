package com.loopers.application.product.result

import com.loopers.domain.product.Product

data class ProductSummaryResult(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
) {
    companion object {
        fun from(product: Product): ProductSummaryResult =
            ProductSummaryResult(
                id = product.id,
                name = product.name.value,
                price = product.price.value,
                likeCount = product.likeCount,
                brandId = product.brandId,
            )
    }
}
