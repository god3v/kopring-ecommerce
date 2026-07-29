package com.loopers.interfaces.api

import com.loopers.support.error.CommonErrorType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus

@DisplayName("ApiControllerAdvice")
class ApiControllerAdviceTest {
    private val advice = ApiControllerAdvice()

    @DisplayName("DataIntegrityViolationException 은 409(CONFLICT) FAIL 응답으로 정규화된다 (500 전파 방지).")
    @Test
    fun normalizesDataIntegrityViolationToConflict() {
        val response = advice.handleConflict(DataIntegrityViolationException("Duplicate entry for key 'uk_users_login_id'"))

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
            { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.FAIL) },
            { assertThat(response.body?.meta?.errorCode).isEqualTo(CommonErrorType.CONFLICT.code) },
        )
    }
}
