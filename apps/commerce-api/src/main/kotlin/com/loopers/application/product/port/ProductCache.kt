package com.loopers.application.product.port

import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.domain.brand.Brand
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult

/**
 * 상품 조회 캐시 (outbound port).
 * 어댑터는 저장소 장애를 흡수한다 — get 은 null(miss), put/evict 은 no-op 로 폴백해 DB 조회를 막지 않는다.
 */
interface ProductCache {
    fun getDetail(productId: Long): CachedProductDetail?
    fun putDetail(detail: CachedProductDetail)
    fun evictDetail(productId: Long)

    fun getList(brandId: Long?, sort: ProductSortType, page: Int, size: Int): PageResult<ProductSummaryResult>?
    fun putList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
        result: PageResult<ProductSummaryResult>,
    )
}

/**
 * 상세 조회의 캐시 가능한 "공유" 부분. 유저별 값인 likedByMe 는 캐시하지 않고 매 요청 합성한다.
 */
data class CachedProductDetail(
    val id: Long,
    val name: String,
    val price: Long,
    val likeCount: Long,
    val brandId: Long,
    val brandName: String,
) {
    companion object {
        fun of(product: Product, brand: Brand): CachedProductDetail = CachedProductDetail(
            id = product.id,
            name = product.name.value,
            price = product.price.value,
            likeCount = product.likeCount,
            brandId = brand.id,
            brandName = brand.name.value,
        )
    }
}
