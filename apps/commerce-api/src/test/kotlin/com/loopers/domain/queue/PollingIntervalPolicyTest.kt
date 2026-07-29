package com.loopers.domain.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("PollingIntervalPolicy")
class PollingIntervalPolicyTest {
    @Test
    @DisplayName("곧 입장 구간(rank 0~99)은 1초")
    fun shortIntervalNearFront() {
        assertThat(PollingIntervalPolicy.intervalSeconds(0L)).isEqualTo(1L)
        assertThat(PollingIntervalPolicy.intervalSeconds(99L)).isEqualTo(1L)
    }

    @Test
    @DisplayName("중간 구간(rank 100~999)은 3초")
    fun mediumIntervalMidQueue() {
        assertThat(PollingIntervalPolicy.intervalSeconds(100L)).isEqualTo(3L)
        assertThat(PollingIntervalPolicy.intervalSeconds(999L)).isEqualTo(3L)
    }

    @Test
    @DisplayName("후순위 구간(rank 1000+)은 5초")
    fun longIntervalFarBack() {
        assertThat(PollingIntervalPolicy.intervalSeconds(1000L)).isEqualTo(5L)
        assertThat(PollingIntervalPolicy.intervalSeconds(100_000L)).isEqualTo(5L)
    }

    @Test
    @DisplayName("순번이 없으면(토큰 발급 또는 미진입) 0 — 더 폴링할 이유가 없다")
    fun zeroWhenNotWaiting() {
        assertThat(PollingIntervalPolicy.intervalSeconds(null)).isEqualTo(0L)
    }
}
