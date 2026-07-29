package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Component

@Component
class ProductMetricsRepositoryImpl(
    private val productMetricsJpaRepository: ProductMetricsJpaRepository,
) : ProductMetricsRepository {
    override fun findByProductId(productId: Long): ProductMetrics? =
        productMetricsJpaRepository.findByProductId(productId)?.toModel()

    override fun findByProductIdForUpdate(productId: Long): ProductMetrics? =
        productMetricsJpaRepository.findByProductIdForUpdate(productId)?.toModel()

    override fun save(productMetrics: ProductMetrics): ProductMetrics {
        val entity = productMetricsJpaRepository.findByProductId(productMetrics.productId)
            ?.apply { sync(productMetrics) }
            ?: ProductMetricsEntity.from(productMetrics)
        return productMetricsJpaRepository.save(entity).toModel()
    }
}
