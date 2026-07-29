package com.loopers.application.product.result

import com.loopers.domain.product.Product
import com.loopers.domain.product.SalesStatus

data class AdminProductSummaryResult(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
    val salesStatus: SalesStatus,
) {
    companion object {
        fun from(product: Product): AdminProductSummaryResult =
            AdminProductSummaryResult(
                id = product.id,
                name = product.name.value,
                price = product.price.value,
                likeCount = product.likeCount,
                brandId = product.brandId,
                salesStatus = product.salesStatus,
            )
    }
}
