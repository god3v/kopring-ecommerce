package com.loopers.domain.ranking

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RankingPeriodTest {
    @DisplayName("기간 파라미터를 해석할 때,")
    @Nested
    inner class From {
        @Test
        fun `없으면 일간이 기본이다`() {
            assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY)
            assertThat(RankingPeriod.from("")).isEqualTo(RankingPeriod.DAILY)
        }

        @Test
        fun `대소문자 구분 없이 해석한다`() {
            assertThat(RankingPeriod.from("WEEKLY")).isEqualTo(RankingPeriod.WEEKLY)
            assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY)
            assertThat(RankingPeriod.from("Monthly")).isEqualTo(RankingPeriod.MONTHLY)
            assertThat(RankingPeriod.from("daily")).isEqualTo(RankingPeriod.DAILY)
        }

        @Test
        fun `알 수 없는 값이면 RANKING_BAD_REQUEST 가 발생한다`() {
            val ex = assertThrows<CoreException> { RankingPeriod.from("yearly") }

            assertThat(ex.errorType).isEqualTo(RankingErrorType.RANKING_BAD_REQUEST)
        }
    }
}
