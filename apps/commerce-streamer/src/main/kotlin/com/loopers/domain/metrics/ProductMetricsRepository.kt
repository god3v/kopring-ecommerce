package com.loopers.domain.metrics

interface ProductMetricsRepository {
    fun findByProductId(productId: Long): ProductMetrics?

    /**
     * 증분 반영 경로 전용 비관적 쓰기 락 조회 — 같은 상품 지표를 서로 다른 소비자(catalog/order)가
     * 동시에 read-modify-write 하면 마지막 저장이 앞선 증분을 덮어쓰므로, 행 락으로 직렬화한다.
     */
    fun findByProductIdForUpdate(productId: Long): ProductMetrics?

    fun save(productMetrics: ProductMetrics): ProductMetrics
}
