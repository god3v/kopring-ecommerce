package com.loopers.domain.metrics

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductMetricsTest {
    @Test
    fun `create 는 모든 카운터가 0 인 상품 지표를 만든다`() {
        val metrics = ProductMetrics.create(productId = 1L)

        assertThat(metrics.productId).isEqualTo(1L)
        assertThat(metrics.likeCount).isZero()
        assertThat(metrics.salesCount).isZero()
        assertThat(metrics.viewCount).isZero()
    }

    @Test
    fun `좋아요 생성은 좋아요 수를 1 늘리고, 취소는 1 줄인다`() {
        val metrics = ProductMetrics.create(productId = 1L)

        metrics.increaseLike()
        metrics.increaseLike()
        metrics.decreaseLike()

        assertThat(metrics.likeCount).isEqualTo(1L)
    }

    @Test
    fun `좋아요 수는 0 미만으로 내려가지 않는다`() {
        val metrics = ProductMetrics.create(productId = 1L)

        metrics.decreaseLike()

        assertThat(metrics.likeCount).isZero()
    }

    @Test
    fun `판매는 수량만큼 판매량을 누적한다`() {
        val metrics = ProductMetrics.create(productId = 1L)

        metrics.addSales(quantity = 3)
        metrics.addSales(quantity = 2)

        assertThat(metrics.salesCount).isEqualTo(5L)
    }

    @Test
    fun `조회는 조회 수를 1 늘린다`() {
        val metrics = ProductMetrics.create(productId = 1L)

        metrics.increaseView()
        metrics.increaseView()

        assertThat(metrics.viewCount).isEqualTo(2L)
    }
}
