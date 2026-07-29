package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductPeriodMetrics
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(properties = ["spring.batch.job.enabled=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@Transactional
class ProductPeriodMetricsEntityIntegrationTest @Autowired constructor(
    private val entityManager: EntityManager,
) {
    private fun metrics(productId: Long = 1L, periodKey: String = "2026W30"): ProductPeriodMetrics =
        ProductPeriodMetrics.of(
            productId = productId,
            periodKey = periodKey,
            viewCount = 10L,
            likeCount = -2L,
            orderQuantity = 3L,
        )

    @DisplayName("주간 집계 테이블은,")
    @Nested
    inner class Weekly {
        @Test
        fun `기간 집계를 저장하고 그대로 읽는다`() {
            val saved = ProductWeeklyMetricsEntity.from(metrics())
            entityManager.persist(saved)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(ProductWeeklyMetricsEntity::class.java, saved.id)

            assertThat(found.productId).isEqualTo(1L)
            assertThat(found.periodKey).isEqualTo("2026W30")
            assertThat(found.viewCount).isEqualTo(10L)
            assertThat(found.likeCount).isEqualTo(-2L)
            assertThat(found.orderQuantity).isEqualTo(3L)
        }

        @Test
        fun `같은 (상품, 기간 키) 를 두 번 저장하면 제약 위반이다`() {
            entityManager.persist(ProductWeeklyMetricsEntity.from(metrics()))

            // id 전략이 IDENTITY 라 persist 시점에 즉시 INSERT 된다 — 예외는 두 번째 persist 에서 발생한다.
            assertThrows<PersistenceException> {
                entityManager.persist(ProductWeeklyMetricsEntity.from(metrics()))
                entityManager.flush()
            }
        }
    }

    @DisplayName("월간 집계 테이블은,")
    @Nested
    inner class Monthly {
        @Test
        fun `기간 집계를 저장하고 그대로 읽는다`() {
            val saved = ProductMonthlyMetricsEntity.from(metrics(periodKey = "202607"))
            entityManager.persist(saved)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(ProductMonthlyMetricsEntity::class.java, saved.id)

            assertThat(found.periodKey).isEqualTo("202607")
            assertThat(found.likeCount).isEqualTo(-2L)
        }

        @Test
        fun `같은 (상품, 기간 키) 를 두 번 저장하면 제약 위반이다`() {
            entityManager.persist(ProductMonthlyMetricsEntity.from(metrics(periodKey = "202607")))

            assertThrows<PersistenceException> {
                entityManager.persist(ProductMonthlyMetricsEntity.from(metrics(periodKey = "202607")))
                entityManager.flush()
            }
        }
    }
}
