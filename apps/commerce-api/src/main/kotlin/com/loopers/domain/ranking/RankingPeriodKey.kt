package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.temporal.IsoFields

/**
 * 주간·월간 랭킹 MV 의 기간 키 계산 — 주간은 ISO-8601 주(`yyyy'W'ww`), 월간은 달력 월(`yyyyMM`).
 * 키 포맷은 배치(쓰기)와 동일하게 유지해야 하는 계약이다 — 바꾸면 양쪽을 함께 바꾼다. 포맷은 테스트로 고정한다.
 */
object RankingPeriodKey {
    fun weeklyOf(date: LocalDate): String =
        "%04dW%02d".format(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

    fun monthlyOf(date: LocalDate): String = "%04d%02d".format(date.year, date.monthValue)
}
