package com.loopers.domain.like

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class LikeErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    LIKE_FORBIDDEN(ErrorStatus.FORBIDDEN, "LIKE_FORBIDDEN", "다른 사용자의 좋아요 목록은 조회할 수 없습니다."),
}
