package com.loopers.infrastructure.ranking

import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "mv_product_rank_monthly",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_mv_product_rank_monthly_rank", columnNames = ["period_key", "rank_no"]),
        UniqueConstraint(name = "uk_mv_product_rank_monthly_product", columnNames = ["period_key", "product_id"]),
    ],
)
class ProductRankMonthlyMvEntity private constructor(
    periodKey: String,
    rankNo: Int,
    productId: Long,
    score: Double,
) : ProductRankMvEntity(periodKey, rankNo, productId, score) {
    companion object {
        fun of(periodKey: String, rankNo: Int, productId: Long, score: Double): ProductRankMonthlyMvEntity =
            ProductRankMonthlyMvEntity(periodKey, rankNo, productId, score)
    }
}
