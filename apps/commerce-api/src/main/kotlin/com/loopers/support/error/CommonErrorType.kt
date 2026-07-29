package com.loopers.support.error

enum class CommonErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    /** 범용 에러 */
    INTERNAL_ERROR(ErrorStatus.INTERNAL_ERROR, "Internal Server Error", "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(ErrorStatus.BAD_REQUEST, "Bad Request", "잘못된 요청입니다."),
    UNAUTHORIZED(ErrorStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패하였습니다."),
    NOT_FOUND(ErrorStatus.NOT_FOUND, "Not Found", "존재하지 않는 요청입니다."),
    CONFLICT(ErrorStatus.CONFLICT, "Conflict", "이미 존재하는 리소스입니다."),
}
