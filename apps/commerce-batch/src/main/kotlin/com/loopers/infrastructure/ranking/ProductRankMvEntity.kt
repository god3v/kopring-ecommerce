package com.loopers.infrastructure.ranking

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.MappedSuperclass

/**
 * 기간(주/월) 랭킹 MV 테이블의 공통 컬럼 — 조회 전용 완제품(TOP 100)의 한 행. 테이블·제약은 하위 엔티티가 소유한다.
 * commerce-api 가 같은 테이블을 읽기 전용으로 매핑하는 계약이다 — 스키마를 바꾸면 양쪽을 함께 바꾼다.
 * rank 는 MySQL 예약어라 컬럼명은 rank_no 를 쓴다.
 */
@MappedSuperclass
abstract class ProductRankMvEntity(
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
