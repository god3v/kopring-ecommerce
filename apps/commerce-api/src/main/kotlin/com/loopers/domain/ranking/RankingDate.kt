package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 랭킹 조회의 기준 날짜 해석 — yyyyMMdd 문자열을 검증하고, 없으면 오늘로 기본 지정한다.
 * 일간 키와 주간·월간 기간 키가 같은 날짜 해석을 공유한다.
 */
object RankingDate {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun resolve(dateString: String?, today: LocalDate): LocalDate {
        if (dateString.isNullOrBlank()) return today
        return try {
            LocalDate.parse(dateString, DATE_FORMAT)
        } catch (e: DateTimeParseException) {
            throw CoreException(RankingErrorType.RANKING_BAD_REQUEST, "날짜 형식은 yyyyMMdd 여야 합니다: $dateString")
        }
    }
}
