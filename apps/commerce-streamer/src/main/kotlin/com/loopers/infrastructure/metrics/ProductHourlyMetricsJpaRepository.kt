package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductSignalSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface ProductHourlyMetricsJpaRepository : JpaRepository<ProductHourlyMetricsEntity, Long> {
    @Query(
        """
        select new com.loopers.domain.metrics.ProductSignalSummary(
            e.productId, sum(e.viewCount), sum(e.likeCount), sum(e.orderQuantity))
        from ProductHourlyMetricsEntity e
        where e.statHour >= :from and e.statHour < :to
        group by e.productId
        """,
    )
    fun sumBetween(
        @Param("from") from: LocalDateTime,
        @Param("to") to: LocalDateTime,
    ): List<ProductSignalSummary>

    @Modifying
    @Query("delete from ProductHourlyMetricsEntity e where e.productId = :productId")
    fun deleteByProductId(@Param("productId") productId: Long)

    /**
     * (product_id, stat_hour) UNIQUE 에 기대는 원자적 증분 upsert —
     * 한 문장으로 끝나 비관 락·행 생성 경합 처리가 필요 없다.
     */
    @Modifying
    @Query(
        nativeQuery = true,
        value = """
        INSERT INTO product_metrics_hourly
            (product_id, stat_hour, view_count, like_count, order_quantity, created_at, updated_at)
        VALUES (:productId, :statHour, :viewCount, :likeCount, :orderQuantity, NOW(6), NOW(6))
        AS delta
        ON DUPLICATE KEY UPDATE
            view_count = product_metrics_hourly.view_count + delta.view_count,
            like_count = product_metrics_hourly.like_count + delta.like_count,
            order_quantity = product_metrics_hourly.order_quantity + delta.order_quantity,
            updated_at = NOW(6)
        """,
    )
    fun upsert(
        @Param("productId") productId: Long,
        @Param("statHour") statHour: LocalDateTime,
        @Param("viewCount") viewCount: Long,
        @Param("likeCount") likeCount: Long,
        @Param("orderQuantity") orderQuantity: Long,
    )
}
