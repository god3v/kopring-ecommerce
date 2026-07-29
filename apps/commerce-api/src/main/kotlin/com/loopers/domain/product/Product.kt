package com.loopers.domain.product

import com.loopers.support.error.CoreException
import java.time.LocalDateTime

class Product internal constructor(
    val id: Long = 0L,
    name: ProductName,
    price: ProductPrice,
    stock: Stock,
    likeCount: Long,
    val brandId: Long,
    salesStatus: SalesStatus = SalesStatus.ON_SALE,
) {
    var name: ProductName = name
        private set

    var price: ProductPrice = price
        private set

    var stock: Stock = stock
        private set

    var likeCount: Long = likeCount
        private set

    var salesStatus: SalesStatus = salesStatus
        private set

    var deletedAt: LocalDateTime? = null
        private set

    init {
        if (likeCount < 0L) {
            throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "likeCount 는 음수가 될 수 없다.")
        }
        if (brandId < 0L) {
            throw CoreException(ProductErrorType.PRODUCT_BAD_REQUEST, "brandId 는 음수가 될 수 없다.")
        }
    }

    fun deductStock(quantity: Int) {
        stock = stock.deduct(quantity)
    }

    fun restoreStock(quantity: Int) {
        stock = stock.restore(quantity)
    }

    fun increaseLikeCount() {
        likeCount += 1L
    }

    fun decreaseLikeCount() {
        if (likeCount > 0L) {
            likeCount -= 1L
        }
    }

    fun softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun update(name: String, price: Long, salesStatus: SalesStatus) {
        this.name = ProductName.of(name)
        this.price = ProductPrice.of(price)
        this.salesStatus = salesStatus
    }

    companion object {
        fun create(
            name: String,
            price: Long,
            stock: Int,
            likeCount: Long,
            brandId: Long,
        ): Product = Product(
            name = ProductName.of(name),
            price = ProductPrice.of(price),
            stock = Stock.of(stock),
            likeCount = likeCount,
            brandId = brandId,
        )
    }
}
