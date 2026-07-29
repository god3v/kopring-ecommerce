package com.loopers.domain.ranking

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class RankingErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    RANKING_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "RANKING_BAD_REQUEST", "랭킹 조회 요청이 올바르지 않습니다."),
}
