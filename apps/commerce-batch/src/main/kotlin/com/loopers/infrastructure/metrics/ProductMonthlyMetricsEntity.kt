package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductPeriodMetrics
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "product_metrics_monthly",
    uniqueConstraints = [UniqueConstraint(name = "uk_product_metrics_monthly", columnNames = ["product_id", "period_key"])],
    indexes = [Index(name = "idx_product_metrics_monthly_period_key", columnList = "period_key")],
)
class ProductMonthlyMetricsEntity private constructor(
    productId: Long,
    periodKey: String,
    viewCount: Long,
    likeCount: Long,
    orderQuantity: Long,
) : ProductPeriodMetricsEntity(productId, periodKey, viewCount, likeCount, orderQuantity) {
    companion object {
        fun from(metrics: ProductPeriodMetrics): ProductMonthlyMetricsEntity = ProductMonthlyMetricsEntity(
            productId = metrics.productId,
            periodKey = metrics.periodKey,
            viewCount = metrics.viewCount,
            likeCount = metrics.likeCount,
            orderQuantity = metrics.orderQuantity,
        )
    }
}
