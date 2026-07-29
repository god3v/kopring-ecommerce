package com.loopers.application.order.command

data class PlaceOrderCommand(
    val loginId: String,
    val idempotencyKey: String,
    val userCouponId: Long? = null,
    val lines: List<OrderLineCommand>,
)

data class OrderLineCommand(
    val productId: Long,
    val quantity: Int,
)
