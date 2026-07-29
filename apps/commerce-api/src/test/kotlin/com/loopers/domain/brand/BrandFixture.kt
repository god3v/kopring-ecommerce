package com.loopers.domain.brand

object BrandFixture {
    const val DEFAULT_NAME = "애플"

    fun validBrand(name: String = DEFAULT_NAME, id: Long = 0L): Brand =
        Brand(id = id, name = BrandName.of(name))
}
