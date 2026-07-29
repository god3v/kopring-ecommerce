package com.loopers.application.product.command

import com.loopers.domain.product.SalesStatus

data class UpdateProductCommand(
    val name: String,
    val price: Long,
    val salesStatus: SalesStatus,
)
