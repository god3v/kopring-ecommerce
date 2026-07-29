package com.loopers.infrastructure.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

/**
 * 기간(주/월) 랭킹 MV 의 읽기 전용 매핑 공통 컬럼 — 적재는 commerce-batch 가 소유한다.
 * 두 앱이 같은 테이블 규약을 공유하는 계약이다 — 스키마를 바꾸면 양쪽을 함께 바꾼다.
 */
@MappedSuperclass
abstract class ProductRankMvEntity protected constructor(
    periodKey: String,
    rankNo: Int,
    productId: Long,
    score: Double,
) : BaseEntity() {
    @Column(name = "period_key", nullable = false, updatable = false)
    var periodKey: String = periodKey
        protected set

    @Column(name = "rank_no", nullable = false)
    var rankNo: Int = rankNo
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "score", nullable = false)
    var score: Double = score
        protected set
}
