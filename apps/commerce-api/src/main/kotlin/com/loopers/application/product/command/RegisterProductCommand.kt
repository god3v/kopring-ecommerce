package com.loopers.application.product.command

data class RegisterProductCommand(
    val brandId: Long,
    val name: String,
    val price: Long,
    val stock: Int,
)
