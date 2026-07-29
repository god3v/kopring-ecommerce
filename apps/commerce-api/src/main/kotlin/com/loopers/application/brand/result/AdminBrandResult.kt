package com.loopers.application.brand.result

import com.loopers.domain.brand.Brand

data class AdminBrandResult(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(brand: Brand): AdminBrandResult = AdminBrandResult(id = brand.id, name = brand.name.value)
    }
}
