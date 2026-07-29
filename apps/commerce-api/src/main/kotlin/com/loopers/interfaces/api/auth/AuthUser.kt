package com.loopers.interfaces.api.auth

/** 인증을 통과한 회원의 식별 정보. `@LoginUser` 파라미터로 주입된다. */
data class AuthUser(
    val id: Long,
    val loginId: String,
)
