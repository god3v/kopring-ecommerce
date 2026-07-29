package com.loopers.domain.order

import com.loopers.support.error.CoreException

@JvmInline
value class Quantity private constructor(val value: Int) {
    companion object {
        fun of(value: Int): Quantity {
            if (value < 1) {
                throw CoreException(OrderErrorType.INVALID_QUANTITY, "수량은 1 이상이어야 한다.")
            }
            return Quantity(value)
        }
    }
}
