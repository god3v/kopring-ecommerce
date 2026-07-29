package com.loopers.domain.product

import com.loopers.support.error.CoreException

@JvmInline
value class ProductPrice private constructor(val value: Long) {
    companion object {
        fun of(value: Long): ProductPrice {
            if (value < 0L) {
                throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "price 는 음수가 될 수 없다.")
            }
            return ProductPrice(value)
        }
    }
}
