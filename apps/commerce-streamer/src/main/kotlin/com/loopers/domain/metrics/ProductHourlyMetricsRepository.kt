package com.loopers.domain.metrics

import java.time.LocalDate

/**
 * 시간별 상품 신호 집계 outbound port — 랭킹 재계산의 원본(RDB SoT)을 쌓고 읽는다.
 */
interface ProductHourlyMetricsRepository {
    /**
     * (상품, 정시 버킷) 행에 증분을 누적한다. 행이 없으면 만들고, 있으면 합산한다 — 원자적 upsert.
     */
    fun accumulate(delta: ProductHourlyMetrics)

    /**
     * 해당 날짜(KST) 버킷들의 신호 합계를 상품별로 반환한다 — 랭킹 재계산의 입력.
     */
    fun sumByDate(date: LocalDate): List<ProductSignalSummary>

    /**
     * 상품의 시간별 집계 행을 모두 지운다 — 삭제 상품이 재구축으로 되살아나지 않게 한다. 재실행해도 결과가 같다.
     */
    fun removeByProductId(productId: Long)
}
