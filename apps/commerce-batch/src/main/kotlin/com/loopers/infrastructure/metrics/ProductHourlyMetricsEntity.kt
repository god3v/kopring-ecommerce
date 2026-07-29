package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * 시간별 상품 신호 집계의 읽기 전용 매핑 — 적재는 streamer 가 소유하고, 배치는 기간 집계의 원본으로 읽기만 한다.
 * 두 앱이 같은 테이블 규약을 공유하는 계약이다 — 스키마를 바꾸면 양쪽을 함께 바꾼다.
 */
@Entity
@Table(
    name = "product_metrics_hourly",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_metrics_hourly", columnNames = ["product_id", "stat_hour"])],
    indexes = [Index(name = "idx_product_metrics_hourly_stat_hour", columnList = "stat_hour")],
)
class ProductHourlyMetricsEntity private constructor(
    productId: Long,
    statHour: LocalDateTime,
    viewCount: Long,
    likeCount: Long,
    orderQuantity: Long,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false, updatable = false)
    var productId: Long = productId
        protected set

    @Column(name = "stat_hour", nullable = false, updatable = false)
    var statHour: LocalDateTime = statHour
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
