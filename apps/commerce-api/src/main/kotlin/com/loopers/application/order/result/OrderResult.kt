package com.loopers.application.order.result

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderStatus
import java.time.LocalDateTime

data class OrderResult(
    val orderId: Long,
    val userId: Long,
    val orderedAt: LocalDateTime,
    val originalAmount: Long,
    val discountAmount: Long,
    val totalAmount: Long,
    val userCouponId: Long?,
    val status: OrderStatus,
    val lines: List<OrderLineResult>,
) {
    companion object {
        fun from(order: Order): OrderResult = OrderResult(
            orderId = order.id,
            userId = order.userId,
            orderedAt = order.orderedAt,
            originalAmount = order.originalAmount,
            discountAmount = order.discountAmount,
            totalAmount = order.totalAmount,
            userCouponId = order.userCouponId,
            status = order.status,
            lines = order.lines.map { OrderLineResult.from(it) },
        )
    }
}

data class OrderLineResult(
    val productId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val subtotal: Long,
) {
    companion object {
        fun from(line: OrderLine): OrderLineResult = OrderLineResult(
            productId = line.productId,
            productName = line.productName,
            unitPrice = line.unitPrice,
            quantity = line.quantity.value,
            subtotal = line.subtotal,
        )
    }
}
