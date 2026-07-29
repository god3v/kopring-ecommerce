package com.loopers.interfaces.api

import com.loopers.support.error.ErrorStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.http.HttpStatus

@DisplayName("ErrorStatus → HttpStatus 매퍼")
class HttpStatusMapperTest {
    @DisplayName("각 ErrorStatus 는 기대하는 HttpStatus 로 변환된다.")
    @Test
    fun mapsEachErrorStatusToExpectedHttpStatus() {
        assertAll(
            { assertThat(ErrorStatus.BAD_REQUEST.toHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST) },
            { assertThat(ErrorStatus.UNAUTHORIZED.toHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED) },
            { assertThat(ErrorStatus.FORBIDDEN.toHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN) },
            { assertThat(ErrorStatus.NOT_FOUND.toHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND) },
            { assertThat(ErrorStatus.CONFLICT.toHttpStatus()).isEqualTo(HttpStatus.CONFLICT) },
            { assertThat(ErrorStatus.INTERNAL_ERROR.toHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR) },
        )
    }

    @DisplayName("모든 ErrorStatus 값은 변환 매핑을 가진다 (누락 분기 시 컴파일/변환 실패).")
    @Test
    fun everyErrorStatusHasMapping() {
        assertAll(
            ErrorStatus.entries.map { status ->
                { assertThat(status.toHttpStatus()).isNotNull() }
            },
        )
    }
}
