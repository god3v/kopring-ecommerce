package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

// 키 포맷은 batch(쓰기)와 공유하는 계약이다 — batch 쪽 RankingPeriodTest 와 같은 값으로 고정한다.
class RankingPeriodKeyTest {
    @DisplayName("주간 키를 계산할 때,")
    @Nested
    inner class Weekly {
        @Test
        fun `날짜가 속한 ISO 주의 키가 된다`() {
            assertThat(RankingPeriodKey.weeklyOf(LocalDate.of(2026, 7, 21))).isEqualTo("2026W30")
        }

        @Test
        fun `한 자리 주차는 0 으로 채운다`() {
            assertThat(RankingPeriodKey.weeklyOf(LocalDate.of(2026, 1, 5))).isEqualTo("2026W02")
        }

        @Test
        fun `연말에 걸친 주는 달력 연도가 아니라 ISO 주 연도를 따른다`() {
            assertThat(RankingPeriodKey.weeklyOf(LocalDate.of(2027, 1, 1))).isEqualTo("2026W53")
            assertThat(RankingPeriodKey.weeklyOf(LocalDate.of(2026, 1, 1))).isEqualTo("2026W01")
        }
    }

    @DisplayName("월간 키를 계산할 때,")
    @Nested
    inner class Monthly {
        @Test
        fun `날짜가 속한 달의 키가 된다`() {
            assertThat(RankingPeriodKey.monthlyOf(LocalDate.of(2026, 7, 21))).isEqualTo("202607")
        }

        @Test
        fun `한 자리 월은 0 으로 채운다`() {
            assertThat(RankingPeriodKey.monthlyOf(LocalDate.of(2026, 1, 15))).isEqualTo("202601")
        }
    }
}
