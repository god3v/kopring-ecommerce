package com.loopers.domain.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProductHourlyMetricsTest {
    @Test
    fun `발생 시각은 정시 버킷으로 귀속된다`() {
        val metrics = ProductHourlyMetrics.create(productId = 101L, occurredAt = LocalDateTime.of(2026, 7, 16, 10, 37, 42))

        assertThat(metrics.statHour).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 0))
    }

    @Test
    fun `좋아요 취소는 버킷 좋아요 수를 0 아래로도 내린다`() {
        // 어제 좋아요를 오늘 취소하면 오늘 버킷은 -1 이어야 재계산이 랭킹판 증분 경로와 동치가 된다.
        val metrics = ProductHourlyMetrics.create(productId = 101L, occurredAt = LocalDateTime.of(2026, 7, 16, 10, 0))

        metrics.decreaseLike()

        assertThat(metrics.likeCount).isEqualTo(-1L)
    }
}
