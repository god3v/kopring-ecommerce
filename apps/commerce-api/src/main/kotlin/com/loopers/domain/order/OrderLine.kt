package com.loopers.domain.order

import com.loopers.support.error.CoreException

class OrderLine internal constructor(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Quantity,
) {
    val subtotal: Long get() = unitPrice * quantity.value

    init {
        if (productName.isBlank()) {
            throw CoreException(OrderErrorType.LINE_BAD_REQUEST, "productName 은 blank 일 수 없다.")
        }
        if (unitPrice < 0L) {
            throw CoreException(OrderErrorType.LINE_BAD_REQUEST, "unitPrice 는 음수가 될 수 없다.")
        }
    }

    companion object {
        fun create(
            productId: Long,
            productName: String,
            unitPrice: Long,
            quantity: Int,
        ): OrderLine = OrderLine(
            productId = productId,
            productName = productName,
            unitPrice = unitPrice,
            quantity = Quantity.of(quantity),
        )
    }
}
