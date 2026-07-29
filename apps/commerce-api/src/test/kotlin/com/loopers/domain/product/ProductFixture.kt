package com.loopers.domain.product

object ProductFixture {
    const val DEFAULT_NAME = "맥북 프로 14인치"
    const val DEFAULT_PRICE = 2_500_000L
    const val DEFAULT_STOCK = 10
    const val DEFAULT_LIKE_COUNT = 0L
    const val DEFAULT_BRAND_ID = 1L

    fun validProduct(
        id: Long = 0L,
        name: String = DEFAULT_NAME,
        price: Long = DEFAULT_PRICE,
        stock: Int = DEFAULT_STOCK,
        likeCount: Long = DEFAULT_LIKE_COUNT,
        brandId: Long = DEFAULT_BRAND_ID,
    ): Product = Product(
        id = id,
        name = ProductName.of(name),
        price = ProductPrice.of(price),
        stock = Stock.of(stock),
        likeCount = likeCount,
        brandId = brandId,
    )
}
