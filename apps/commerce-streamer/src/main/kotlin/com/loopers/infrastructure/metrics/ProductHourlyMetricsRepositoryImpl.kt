package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductHourlyMetrics
import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.metrics.ProductSignalSummary
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class ProductHourlyMetricsRepositoryImpl(
    private val productHourlyMetricsJpaRepository: ProductHourlyMetricsJpaRepository,
) : ProductHourlyMetricsRepository {
    override fun accumulate(delta: ProductHourlyMetrics) {
        productHourlyMetricsJpaRepository.upsert(
            productId = delta.productId,
            statHour = delta.statHour,
            viewCount = delta.viewCount,
            likeCount = delta.likeCount,
            orderQuantity = delta.orderQuantity,
        )
    }

    override fun sumByDate(date: LocalDate): List<ProductSignalSummary> =
        productHourlyMetricsJpaRepository.sumBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())

    override fun removeByProductId(productId: Long) {
        productHourlyMetricsJpaRepository.deleteByProductId(productId)
    }
}
