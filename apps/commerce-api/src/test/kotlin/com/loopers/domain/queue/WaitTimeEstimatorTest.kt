package com.loopers.domain.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WaitTimeEstimator")
class WaitTimeEstimatorTest {
    @Test
    @DisplayName("예상 대기 = 순번 / 초당 처리량 (올림)")
    fun estimatesByThroughput() {
        // 순번 300, 초당 50명 → 300 / 50 = 6초
        assertThat(WaitTimeEstimator.estimateSeconds(300L, 50.0)).isEqualTo(6L)
        // 순번 300, 초당 180명 → 1.67초 → 올림 2초
        assertThat(WaitTimeEstimator.estimateSeconds(300L, 180.0)).isEqualTo(2L)
    }

    @Test
    @DisplayName("순번 0(내 차례)이면 0초")
    fun zeroWhenFront() {
        assertThat(WaitTimeEstimator.estimateSeconds(0L, 180.0)).isEqualTo(0L)
    }

    @Test
    @DisplayName("처리량이 0 이하면 0초(0 나눗셈 방지)")
    fun zeroWhenNoThroughput() {
        assertThat(WaitTimeEstimator.estimateSeconds(300L, 0.0)).isEqualTo(0L)
    }
}
