package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class RankingKeyTest {
    private val today = LocalDate.of(2026, 7, 14)

    @DisplayName("날짜 문자열로 키를 만들 때,")
    @Nested
    inner class FromDateString {
        @Test
        fun `yyyyMMdd 문자열은 그 날짜의 키가 된다`() {
            assertThat(RankingKey.of("20260713", today)).isEqualTo("rank:all:20260713")
        }

        @Test
        fun `문자열이 없으면 오늘 날짜의 키가 된다`() {
            assertThat(RankingKey.of(null, today)).isEqualTo("rank:all:20260714")
        }

        @Test
        fun `yyyyMMdd 형식이 아니면 RANKING_BAD_REQUEST 가 발생한다`() {
            val ex = assertThrows<CoreException> { RankingKey.of("2026-07-14", today) }

            assertThat(ex.errorType).isEqualTo(RankingErrorType.RANKING_BAD_REQUEST)
        }
    }
}
