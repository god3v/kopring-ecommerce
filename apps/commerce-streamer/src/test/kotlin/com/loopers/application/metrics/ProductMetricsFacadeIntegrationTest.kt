package com.loopers.application.metrics

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.infrastructure.metrics.ProcessedEventRepositoryImpl
import com.loopers.infrastructure.metrics.ProductHourlyMetricsRepositoryImpl
import com.loopers.infrastructure.metrics.ProductMetricsRepositoryImpl
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    MySqlTestContainersConfig::class,
    DataSourceConfig::class,
    ProductMetricsRepositoryImpl::class,
    ProductHourlyMetricsRepositoryImpl::class,
    ProcessedEventRepositoryImpl::class,
    ProductMetricsFacade::class,
)
class ProductMetricsFacadeIntegrationTest @Autowired constructor(
    private val productMetricsFacade: ProductMetricsFacade,
    private val productMetricsRepository: ProductMetricsRepository,
    private val productHourlyMetricsRepository: ProductHourlyMetricsRepository,
) {
    private val occurredAt = LocalDateTime.of(2026, 7, 16, 10, 30)
    private val date = LocalDate.of(2026, 7, 16)

    @Test
    fun `좋아요 생성 이벤트는 상품 지표의 좋아요 수를 1 늘린다`() {
        productMetricsFacade.increaseLike(eventId = UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)

        assertThat(productMetricsRepository.findByProductId(1L)!!.likeCount).isEqualTo(1L)
    }

    @Test
    fun `주문 생성 이벤트는 라인 수량만큼 판매량을 누적한다`() {
        productMetricsFacade.addSales(
            eventId = UUID.randomUUID(),
            lines = listOf(SalesLine(productId = 1L, quantity = 3), SalesLine(productId = 2L, quantity = 2)),
            occurredAt = occurredAt,
        )

        assertThat(productMetricsRepository.findByProductId(1L)!!.salesCount).isEqualTo(3L)
        assertThat(productMetricsRepository.findByProductId(2L)!!.salesCount).isEqualTo(2L)
    }

    @Test
    fun `조회 이벤트는 조회 수를 1 늘린다`() {
        productMetricsFacade.increaseView(eventId = UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)

        assertThat(productMetricsRepository.findByProductId(1L)!!.viewCount).isEqualTo(1L)
    }

    @Test
    fun `같은 eventId 로 두 번 도착해도 결과는 1회만 반영된다`() {
        val eventId = UUID.randomUUID()

        productMetricsFacade.increaseLike(eventId = eventId, productId = 1L, occurredAt = occurredAt)
        productMetricsFacade.increaseLike(eventId = eventId, productId = 1L, occurredAt = occurredAt)

        assertThat(productMetricsRepository.findByProductId(1L)!!.likeCount).isEqualTo(1L)
    }

    @Test
    fun `행동 반영은 발생 시각의 시간별 버킷에도 같은 트랜잭션으로 누적된다`() {
        productMetricsFacade.increaseView(UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)
        productMetricsFacade.increaseLike(UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)
        // 다른 시각의 취소도 그날 합계에서 순증으로 상쇄된다.
        productMetricsFacade.decreaseLike(UUID.randomUUID(), productId = 1L, occurredAt = occurredAt.plusHours(1))
        productMetricsFacade.addSales(UUID.randomUUID(), listOf(SalesLine(productId = 1L, quantity = 3)), occurredAt = occurredAt)

        val summary = productHourlyMetricsRepository.sumByDate(date).single()
        assertThat(summary.productId).isEqualTo(1L)
        assertThat(summary.viewCount).isEqualTo(1L)
        assertThat(summary.likeCount).isEqualTo(0L)
        assertThat(summary.orderQuantity).isEqualTo(3L)
    }

    @Test
    fun `같은 eventId 재전달은 시간별 집계에도 다시 누적되지 않는다`() {
        val eventId = UUID.randomUUID()

        productMetricsFacade.increaseView(eventId, productId = 1L, occurredAt = occurredAt)
        productMetricsFacade.increaseView(eventId, productId = 1L, occurredAt = occurredAt)

        assertThat(productHourlyMetricsRepository.sumByDate(date).single().viewCount).isEqualTo(1L)
    }

    @Test
    fun `상품 삭제는 그 상품의 시간별 집계 행만 걷어낸다`() {
        productMetricsFacade.increaseView(UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)
        productMetricsFacade.increaseView(UUID.randomUUID(), productId = 2L, occurredAt = occurredAt)

        productMetricsFacade.removeProduct(UUID.randomUUID(), productId = 1L)

        assertThat(productHourlyMetricsRepository.sumByDate(date).map { it.productId }).containsExactly(2L)
        // 누적 지표는 남는다 — 분석 이력 보존.
        assertThat(productMetricsRepository.findByProductId(1L)!!.viewCount).isEqualTo(1L)
    }

    @Test
    fun `삭제 후 도착한 지연 신호는 시간별 집계를 되살리지 않는다`() {
        productMetricsFacade.removeProduct(UUID.randomUUID(), productId = 1L)

        // 주문 이벤트는 다른 토픽(주문 키)이라 삭제와 순서 보장이 없다 — 삭제 뒤에 도착할 수 있다.
        productMetricsFacade.addSales(UUID.randomUUID(), listOf(SalesLine(productId = 1L, quantity = 3)), occurredAt = occurredAt)
        productMetricsFacade.increaseView(UUID.randomUUID(), productId = 1L, occurredAt = occurredAt)

        assertThat(productHourlyMetricsRepository.sumByDate(date)).isEmpty()
        // 누적 지표는 실제 발생한 사실이므로 계속 쌓인다 — 분석 이력 보존.
        assertThat(productMetricsRepository.findByProductId(1L)!!.salesCount).isEqualTo(3L)
    }
}
