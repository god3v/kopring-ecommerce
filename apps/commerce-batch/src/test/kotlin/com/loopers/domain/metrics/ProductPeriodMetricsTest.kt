package com.loopers.domain.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ProductPeriodMetricsTest {
    @DisplayName("기간 집계를 만들 때,")
    @Nested
    inner class Of {
        @Test
        fun `상품·기간 키와 신호 개수를 그대로 보유한다`() {
            val metrics = ProductPeriodMetrics.of(
                productId = 1L,
                periodKey = "2026W30",
                viewCount = 100L,
                likeCount = 5L,
                orderQuantity = 3L,
            )

            assertThat(metrics.productId).isEqualTo(1L)
            assertThat(metrics.periodKey).isEqualTo("2026W30")
            assertThat(metrics.viewCount).isEqualTo(100L)
            assertThat(metrics.likeCount).isEqualTo(5L)
            assertThat(metrics.orderQuantity).isEqualTo(3L)
        }

        @Test
        fun `좋아요 순증이 음수여도 0 으로 자르지 않는다`() {
            val metrics = ProductPeriodMetrics.of(
                productId = 1L,
                periodKey = "2026W30",
                viewCount = 0L,
                likeCount = -2L,
                orderQuantity = 0L,
            )

            assertThat(metrics.likeCount).isEqualTo(-2L)
        }
    }
}
