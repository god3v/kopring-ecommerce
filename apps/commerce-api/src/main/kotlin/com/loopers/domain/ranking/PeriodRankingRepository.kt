package com.loopers.domain.ranking

/**
 * 주간·월간 랭킹 MV 조회 outbound port. 배치가 적재한 TOP 100 완제품을 읽기만 한다 — 적재는 배치가 소유한다.
 * 일간은 이 port 가 아니라 Redis 랭킹판(RankingRepository)이 담당한다.
 */
interface PeriodRankingRepository {
    /**
     * 순위 오름차순으로 페이지를 슬라이스해 반환한다.
     */
    fun topN(period: RankingPeriod, periodKey: String, page: Int, size: Int): List<RankedEntry>

    /**
     * 기간 키에 적재된 상품 수를 반환한다. 없으면 0.
     */
    fun size(period: RankingPeriod, periodKey: String): Long
}
