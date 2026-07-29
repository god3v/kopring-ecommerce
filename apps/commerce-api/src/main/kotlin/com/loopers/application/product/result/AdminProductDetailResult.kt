package com.loopers.application.product.result

import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.SalesStatus

data class AdminProductDetailResult(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
    val salesStatus: SalesStatus,
) {
    companion object {
        fun of(product: Product, brand: Brand): AdminProductDetailResult =
            AdminProductDetailResult(
                id = product.id,
                name = product.name.value,
                price = product.price.value,
                likeCount = product.likeCount,
                brandId = brand.id,
                brandName = brand.name.value,
                salesStatus = product.salesStatus,
            )
    }
}
