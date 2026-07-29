package com.loopers.domain.order

import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.support.error.CoreException

object OrderService {
    fun createOrder(
        userId: Long,
        products: Map<Long, Product>,
        quantities: Map<Long, Int>,
        idempotencyKey: String,
    ): Order {
        val lines = quantities.map { (productId, quantity) ->
            val product = products[productId]
                ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
            product.deductStock(quantity)
            OrderLine.create(
                productId = productId,
                productName = product.name.value,
                unitPrice = product.price.value,
                quantity = quantity,
            )
        }
        return Order.create(
            userId = userId,
            lines = lines,
            idempotencyKey = idempotencyKey,
        )
    }
}
