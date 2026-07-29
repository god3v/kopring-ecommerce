package com.loopers.application.product.query

import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageQuery

data class GetProductsQuery(
    val sort: ProductSortType? = ProductSortType.LATEST,
    val brandId: Long?,
    val paging: PageQuery,
)
