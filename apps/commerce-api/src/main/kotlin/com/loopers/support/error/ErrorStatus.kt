package com.loopers.support.error

/** 프레임워크 무관한 에러 의미 분류. HTTP 상태 변환은 interfaces 매퍼가 맡는다. */
enum class ErrorStatus {
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR,
}
