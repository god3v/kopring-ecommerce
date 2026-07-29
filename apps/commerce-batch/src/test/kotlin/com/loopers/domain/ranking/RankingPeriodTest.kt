package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RankingPeriodTest {
    @DisplayName("주간 기간을 계산할 때,")
    @Nested
    inner class Weekly {
        @Test
        fun `날짜가 속한 ISO 주의 키가 된다`() {
            val period = RankingPeriod.weeklyOf(LocalDate.of(2026, 7, 21))

            assertThat(period.key).isEqualTo("2026W30")
        }

        @Test
        fun `한 자리 주차는 0 으로 채운다`() {
            val period = RankingPeriod.weeklyOf(LocalDate.of(2026, 1, 5))

            assertThat(period.key).isEqualTo("2026W02")
        }

        @Test
        fun `집계 창은 월요일 0시부터 다음 월요일 0시 전까지다`() {
            val period = RankingPeriod.weeklyOf(LocalDate.of(2026, 7, 21))

            assertThat(period.start).isEqualTo(LocalDateTime.of(2026, 7, 20, 0, 0))
            assertThat(period.end).isEqualTo(LocalDateTime.of(2026, 7, 27, 0, 0))
        }

        @Test
        fun `연초 날짜가 전년 12월에 시작하는 주에 속하면 창 시작은 전년이다`() {
            val period = RankingPeriod.weeklyOf(LocalDate.of(2026, 1, 1))

            assertThat(period.key).isEqualTo("2026W01")
            assertThat(period.start).isEqualTo(LocalDateTime.of(2025, 12, 29, 0, 0))
        }

        @Test
        fun `연말에 걸친 주는 달력 연도가 아니라 ISO 주 연도를 따른다`() {
            val period = RankingPeriod.weeklyOf(LocalDate.of(2027, 1, 1))

            assertThat(period.key).isEqualTo("2026W53")
            assertThat(period.start).isEqualTo(LocalDateTime.of(2026, 12, 28, 0, 0))
            assertThat(period.end).isEqualTo(LocalDateTime.of(2027, 1, 4, 0, 0))
        }
    }

    @DisplayName("월간 기간을 계산할 때,")
    @Nested
    inner class Monthly {
        @Test
        fun `날짜가 속한 달의 키가 된다`() {
            val period = RankingPeriod.monthlyOf(LocalDate.of(2026, 7, 21))

            assertThat(period.key).isEqualTo("202607")
        }

        @Test
        fun `한 자리 월은 0 으로 채운다`() {
            val period = RankingPeriod.monthlyOf(LocalDate.of(2026, 1, 15))

            assertThat(period.key).isEqualTo("202601")
        }

        @Test
        fun `집계 창은 1일 0시부터 다음 달 1일 0시 전까지다`() {
            val period = RankingPeriod.monthlyOf(LocalDate.of(2026, 7, 21))

            assertThat(period.start).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0))
            assertThat(period.end).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0))
        }
    }
}
