package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import com.loopers.domain.metrics.ProductHourlyMetrics
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

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

    companion object {
        fun from(delta: ProductHourlyMetrics): ProductHourlyMetricsEntity = ProductHourlyMetricsEntity(
            productId = delta.productId,
            statHour = delta.statHour,
            viewCount = delta.viewCount,
            likeCount = delta.likeCount,
            orderQuantity = delta.orderQuantity,
        )
    }
}
