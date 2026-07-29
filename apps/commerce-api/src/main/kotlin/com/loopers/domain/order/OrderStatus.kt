package com.loopers.domain.order

enum class OrderStatus(val key: String) {
    CREATED("created"),
    PAYMENT_PENDING("payment_pending"),
    PAID("paid"),
    PAYMENT_FAILED("payment_failed"),
    CANCELED("canceled"),
}
