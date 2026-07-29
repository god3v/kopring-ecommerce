package com.loopers.domain.product

import com.loopers.support.page.PageResult

interface ProductRepository {
    fun save(product: Product): Product
    fun saveAll(products: Collection<Product>): List<Product>
    fun findById(id: Long): Product?
    fun findAllByIds(ids: Collection<Long>): List<Product>
    fun findAllByIdsForUpdate(ids: Collection<Long>): List<Product>

    /**
     * 보상(재고 복원) 경로 전용 — soft-delete 된 상품도 비관적 쓰기 락으로 함께 조회한다.
     * 주문 스냅샷에 기록된 상품은 delist 되어도 재고 원장을 되돌려야 하므로 삭제 마크 행까지 포함한다.
     */
    fun findAllByIdsForUpdateIncludingDeleted(ids: Collection<Long>): List<Product>
    fun increaseLikeCount(productId: Long)
    fun decreaseLikeCount(productId: Long)

    fun findAll(
        sort: ProductSortType,
        brandId: Long?,
        page: Int,
        size: Int,
    ): PageResult<Product>

    fun findAllForAdmin(
        brandId: Long?,
        page: Int,
        size: Int,
    ): PageResult<Product>

    fun findAllByBrandId(brandId: Long): List<Product>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean
}
