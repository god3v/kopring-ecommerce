package com.loopers.domain.product

import com.loopers.support.error.CoreException

@JvmInline
value class Stock private constructor(val value: Int) {
    fun deduct(quantity: Int): Stock {
        if (quantity <= 0) {
            throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "차감 수량은 양수여야 한다.")
        }
        if (value - quantity < 0) {
            throw CoreException(ProductErrorType.INSUFFICIENT_STOCK, "재고가 부족하다.")
        }
        return Stock(value - quantity)
    }

    fun restore(quantity: Int): Stock {
        if (quantity <= 0) {
            throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "복원 수량은 양수여야 한다.")
        }
        return Stock(value + quantity)
    }

    companion object {
        fun of(value: Int): Stock {
            if (value < 0) {
                throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "stock 은 음수가 될 수 없다.")
            }
            return Stock(value)
        }
    }
}
