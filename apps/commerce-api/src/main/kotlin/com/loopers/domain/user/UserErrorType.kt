package com.loopers.domain.user

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class UserErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    DUPLICATE_LOGIN_ID(ErrorStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 로그인 ID 입니다."),
    DUPLICATE_EMAIL(ErrorStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다."),
    SIGNUP_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "SIGNUP_BAD_REQUEST", "회원가입 입력값이 올바르지 않습니다."),
    UNAUTHORIZED(ErrorStatus.UNAUTHORIZED, "UNAUTHORIZED", "인증에 실패했습니다."),
    PASSWORD_CHANGE_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "PASSWORD_CHANGE_BAD_REQUEST", "비밀번호 변경 입력값이 올바르지 않습니다."),
    INVALID_PASSWORD(ErrorStatus.BAD_REQUEST, "INVALID_PASSWORD", "유효한 비밀번호가 아닙니다."),
}
