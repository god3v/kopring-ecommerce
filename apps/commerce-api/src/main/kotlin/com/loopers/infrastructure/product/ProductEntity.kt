package com.loopers.infrastructure.product

import com.loopers.domain.BaseEntity
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductName
import com.loopers.domain.product.ProductPrice
import com.loopers.domain.product.SalesStatus
import com.loopers.domain.product.Stock
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

// 목록 조회용 복합 인덱스 — (brand_id 필터) + (정렬 컬럼) + (id 타이브레이크) 순.
// 타이브레이크 방향을 주 정렬과 통일해, 오름차순 인덱스 하나로 정·역방향 스캔을 모두 커버한다.
@Entity
@Table(
    name = "products",
    indexes = [
        Index(name = "idx_products_created_id", columnList = "created_at, id"),
        Index(name = "idx_products_price_id", columnList = "price, id"),
        Index(name = "idx_products_like_id", columnList = "like_count, id"),
        Index(name = "idx_products_brand_created_id", columnList = "brand_id, created_at, id"),
        Index(name = "idx_products_brand_price_id", columnList = "brand_id, price, id"),
        Index(name = "idx_products_brand_like_id", columnList = "brand_id, like_count, id"),
    ],
)
@SQLRestriction("deleted_at IS NULL")
class ProductEntity private constructor(
    name: String,
    price: Long,
    stock: Int,
    likeCount: Long,
    brandId: Long,
    salesStatus: SalesStatus,
) : BaseEntity() {
    @Column(nullable = false)
    var name: String = name
        protected set

    @Column(nullable = false)
    var price: Long = price
        protected set

    @Column(nullable = false)
    var stock: Int = stock
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set

    @Column(name = "brand_id", nullable = false)
    var brandId: Long = brandId
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_status", nullable = false)
    var salesStatus: SalesStatus = salesStatus
        protected set

    fun toDomain(): Product = Product(
        id = this.id,
        name = ProductName.of(this.name),
        price = ProductPrice.of(this.price),
        stock = Stock.of(this.stock),
        likeCount = this.likeCount,
        brandId = this.brandId,
        salesStatus = this.salesStatus,
    )

    fun syncFrom(product: Product) {
        this.name = product.name.value
        this.price = product.price.value
        this.stock = product.stock.value
        this.likeCount = product.likeCount
        this.salesStatus = product.salesStatus
        if (product.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(product: Product): ProductEntity = ProductEntity(
            name = product.name.value,
            price = product.price.value,
            stock = product.stock.value,
            likeCount = product.likeCount,
            brandId = product.brandId,
            salesStatus = product.salesStatus,
        )
    }
}
