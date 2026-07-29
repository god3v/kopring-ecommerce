package com.loopers.domain.ranking

/**
 * 랭킹판 조회 outbound port. 랭킹판을 읽기만 한다 — 점수 갱신은 집계 파이프라인이 소유한다.
 */
interface RankingRepository {
    /**
     * 점수 내림차순으로 offset 부터 size 개의 랭킹 항목을 반환한다.
     */
    fun topN(key: String, offset: Long, size: Long): List<RankedEntry>

    /**
     * 상품의 순위를 1부터 매겨 반환한다. 랭킹판에 없으면 null.
     */
    fun rankOf(key: String, productId: Long): Long?

    /**
     * 랭킹판에 담긴 상품 수를 반환한다. 랭킹판이 없으면 0.
     */
    fun size(key: String): Long
}
