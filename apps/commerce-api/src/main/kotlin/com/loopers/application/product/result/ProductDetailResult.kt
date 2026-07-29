package com.loopers.application.product.result

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product

data class ProductDetailResult(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
    val likedByMe: Boolean,
    val rank: Long? = null,
) {
    companion object {
        fun of(product: Product, brand: Brand, likedByMe: Boolean): ProductDetailResult =
            ProductDetailResult(
                id = product.id,
                name = product.name.value,
                price = product.price.value,
                likeCount = product.likeCount,
                brandId = brand.id,
                brandName = brand.name.value,
                likedByMe = likedByMe,
            )
    }
}
