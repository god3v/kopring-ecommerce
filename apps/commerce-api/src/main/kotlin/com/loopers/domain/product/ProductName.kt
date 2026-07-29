package com.loopers.domain.product

import com.loopers.support.error.CoreException

@JvmInline
value class ProductName private constructor(val value: String) {
    companion object {
        fun of(value: String): ProductName {
            if (value.isBlank()) {
                throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "name 은 비어 있을 수 없다.")
            }
            return ProductName(value)
        }
    }
}
