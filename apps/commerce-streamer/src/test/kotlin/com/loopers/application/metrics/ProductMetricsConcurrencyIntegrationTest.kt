package com.loopers.application.metrics

import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 상품 지표 증분의 동시성 보장 검증 — 같은 상품 지표 행을 서로 다른 이벤트(좋아요·판매)가 동시에 갱신해도
 * 증분이 소실되지 않는다. 행 비관 락이 read-modify-write 를 직렬화하므로 성립한다.
 * (catalog-events 는 key=productId, order-events 는 key=orderId 라 두 소비자가 같은 상품 행을 동시에 만질 수 있다.)
 */
@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class ProductMetricsConcurrencyIntegrationTest @Autowired constructor(
    private val facade: ProductMetricsFacade,
    private val productMetricsRepository: ProductMetricsRepository,
    private val productHourlyMetricsRepository: ProductHourlyMetricsRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `같은 상품에 좋아요 증가와 판매 누적이 동시에 몰려도 증분이 소실되지 않는다`() {
        val productId = 1L
        val occurredAt = java.time.LocalDateTime.of(2026, 7, 16, 10, 0)
        // 최초 생성 경합을 배제하고 갱신 경합만 검증한다 — 행을 먼저 만들어 둔다.
        facade.increaseView(UUID.randomUUID(), productId, occurredAt)

        val likeEvents = 20
        val salesEvents = 20
        val quantityPerSale = 2

        val pool = Executors.newFixedThreadPool(16)
        val ready = CountDownLatch(1)
        val tasks: List<() -> Unit> =
            (1..likeEvents).map {
                {
                    ready.await()
                    facade.increaseLike(UUID.randomUUID(), productId, occurredAt)
                }
            } + (1..salesEvents).map {
                {
                    ready.await()
                    facade.addSales(UUID.randomUUID(), listOf(SalesLine(productId, quantityPerSale)), occurredAt)
                }
            }
        val futures = tasks.shuffled().map { task -> pool.submit { task() } }
        ready.countDown()
        futures.forEach { it.get() }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        val metrics = productMetricsRepository.findByProductId(productId)!!
        assertThat(metrics.likeCount).isEqualTo(likeEvents.toLong())
        assertThat(metrics.salesCount).isEqualTo((salesEvents * quantityPerSale).toLong())
        assertThat(metrics.viewCount).isEqualTo(1L)
    }

    @Test
    fun `삭제와 누적이 동시에 실행돼도 시간별 집계에 삭제 상품이 남지 않는다`() {
        val productId = 2L
        val occurredAt = java.time.LocalDateTime.of(2026, 7, 16, 10, 0)
        facade.increaseView(UUID.randomUUID(), productId, occurredAt)

        val salesEvents = 30
        val pool = Executors.newFixedThreadPool(16)
        val ready = CountDownLatch(1)
        // 삭제 1건과 누적 N건이 어떤 순서로 섞여도 결과는 같아야 한다 —
        // 삭제 전 누적분은 삭제가 걷어내고, 삭제 후 누적은 표식이 막는다(같은 지표 행 잠금으로 직렬화).
        val tasks: List<() -> Unit> =
            (1..salesEvents).map {
                {
                    ready.await()
                    facade.addSales(UUID.randomUUID(), listOf(SalesLine(productId, 1)), occurredAt)
                }
            } + listOf(
                {
                    ready.await()
                    facade.removeProduct(UUID.randomUUID(), productId)
                },
            )
        val futures = tasks.shuffled().map { task -> pool.submit { task() } }
        ready.countDown()
        futures.forEach { it.get() }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        assertThat(productHourlyMetricsRepository.sumByDate(LocalDate.of(2026, 7, 16))).isEmpty()
    }
}
