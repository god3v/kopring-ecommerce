package com.loopers.domain.queue

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class QueueErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    ENTRY_TOKEN_REQUIRED(ErrorStatus.FORBIDDEN, "ENTRY_TOKEN_REQUIRED", "입장 토큰이 필요합니다. 대기열을 통해 입장해 주세요."),
    ENTRY_TOKEN_INVALID(ErrorStatus.FORBIDDEN, "ENTRY_TOKEN_INVALID", "유효하지 않거나 만료된 입장 토큰입니다."),
}
