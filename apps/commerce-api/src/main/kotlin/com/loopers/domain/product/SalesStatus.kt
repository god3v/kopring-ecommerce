package com.loopers.domain.product

import com.loopers.support.error.CoreException

enum class SalesStatus(val key: String) {
    ON_SALE("on_sale"),
    OUT_OF_STOCK("out_of_stock"),
    OFF_SALE("off_sale"),
    ;

    companion object {
        fun from(value: String): SalesStatus =
            entries.firstOrNull { it.key == value }
                ?: throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "지원하지 않는 판매 상태이다.")
    }
}
