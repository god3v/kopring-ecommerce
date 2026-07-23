package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

/**
 * 기간(주/월) 상품 신호 집계 테이블의 공통 컬럼. 테이블·제약은 하위 엔티티가 소유한다.
 */
@MappedSuperclass
abstract class ProductPeriodMetricsEntity(
    productId: Long,
    periodKey: String,
    viewCount: Long,
    likeCount: Long,
    orderQuantity: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "period_key", nullable = false, updatable = false)
    var periodKey: String = periodKey
        protected set

    @Column(name = "view_count", nullable = false)
    var viewCount: Long = viewCount
        protected set

    @Column(name = "like_count", nullable = false)
    var likeCount: Long = likeCount
        protected set

    @Column(name = "order_quantity", nullable = false)
    var orderQuantity: Long = orderQuantity
        protected set
}
