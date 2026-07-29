package com.loopers.domain.product

import com.loopers.support.error.CoreException

enum class ProductSortType(val key: String) {
    LATEST("latest"),
    PRICE_ASC("price_asc"),
    LIKES_DESC("likes_desc"),
    ;

    companion object {
        fun from(value: String?): ProductSortType {
            if (value == null) return LATEST
            return entries.firstOrNull { it.key == value }
                ?: throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "지원하지 않는 정렬 옵션이다.")
        }
    }
}
