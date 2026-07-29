package com.loopers.domain.ranking

import com.loopers.support.error.CoreException

/**
 * 랭킹 조회 기간 — 일간은 Redis 랭킹판, 주간·월간은 배치가 적재한 MV 를 읽는다.
 * 조회 파라미터로 받은 문자열을 검증하고, 없으면 일간으로 기본 지정한다.
 */
enum class RankingPeriod {
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    companion object {
        fun from(value: String?): RankingPeriod {
            if (value.isNullOrBlank()) return DAILY
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw CoreException(
                    RankingErrorType.RANKING_BAD_REQUEST,
                    "period 는 DAILY, WEEKLY, MONTHLY 중 하나여야 합니다: $value",
                )
        }
    }
}
