package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

/**
 * 상품 누적 집계의 읽기 전용 매핑 — 배치는 삭제 표식(deleted_at) 판별에만 쓴다.
 * 스키마 소유는 streamer 다. 배치가 쓰는 컬럼만 매핑한다.
 */
@Entity
@Table(name = "product_metrics")
class ProductMetricsEntity private constructor(
    productId: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, unique = true, updatable = false)
    var productId: Long = productId
        protected set
}
