package com.loopers.domain.ranking

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/**
 * 랭킹 집계 기간 — 기간 키와 집계 창 [start, end) 을 함께 계산한다.
 * 주간은 ISO-8601 주(월요일 시작, 주 연도 기준 `yyyy'W'ww`), 월간은 달력 월(`yyyyMM`).
 * 키 포맷은 배치(쓰기)와 commerce-api(읽기)가 동일하게 유지해야 하는 계약이다 — 바꾸면 양쪽을 함께 바꾼다.
 */
data class RankingPeriod(
    val key: String,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    companion object {
        fun weeklyOf(date: LocalDate): RankingPeriod {
            val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekYear = date.get(IsoFields.WEEK_BASED_YEAR)
            val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            return RankingPeriod(
                key = "%04dW%02d".format(weekYear, week),
                start = monday.atStartOfDay(),
                end = monday.plusWeeks(1).atStartOfDay(),
            )
        }

        fun monthlyOf(date: LocalDate): RankingPeriod {
            val firstDay = date.withDayOfMonth(1)
            return RankingPeriod(
                key = "%04d%02d".format(date.year, date.monthValue),
                start = firstDay.atStartOfDay(),
                end = firstDay.plusMonths(1).atStartOfDay(),
            )
        }
    }
}
