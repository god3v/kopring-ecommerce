package com.loopers.infrastructure.ranking

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
class ProductRankMvEntityIntegrationTest @Autowired constructor(
    private val entityManager: EntityManager,
) {
    @DisplayName("주간 랭킹 MV 테이블은,")
    @Nested
    inner class Weekly {
        @Test
        fun `랭킹 행을 저장하고 그대로 읽는다`() {
            val saved = ProductRankWeeklyMvEntity.of(periodKey = "2026W30", rankNo = 1, productId = 101L, score = 12.5)
            entityManager.persist(saved)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(ProductRankWeeklyMvEntity::class.java, saved.id)

            assertThat(found.periodKey).isEqualTo("2026W30")
            assertThat(found.rankNo).isEqualTo(1)
            assertThat(found.productId).isEqualTo(101L)
            assertThat(found.score).isEqualTo(12.5)
        }

        @Test
        fun `같은 (기간 키, 순위) 를 두 번 저장하면 제약 위반이다`() {
            entityManager.persist(ProductRankWeeklyMvEntity.of("2026W30", 1, 101L, 12.5))

            // id 전략이 IDENTITY 라 persist 시점에 즉시 INSERT 된다 — 예외는 두 번째 persist 에서 발생한다.
            assertThrows<PersistenceException> {
                entityManager.persist(ProductRankWeeklyMvEntity.of("2026W30", 1, 202L, 10.0))
                entityManager.flush()
            }
        }

        @Test
        fun `같은 (기간 키, 상품) 을 두 번 저장하면 제약 위반이다`() {
            entityManager.persist(ProductRankWeeklyMvEntity.of("2026W30", 1, 101L, 12.5))

            assertThrows<PersistenceException> {
                entityManager.persist(ProductRankWeeklyMvEntity.of("2026W30", 2, 101L, 10.0))
                entityManager.flush()
            }
        }
    }

    @DisplayName("월간 랭킹 MV 테이블은,")
    @Nested
    inner class Monthly {
        @Test
        fun `랭킹 행을 저장하고 그대로 읽는다`() {
            val saved = ProductRankMonthlyMvEntity.of(periodKey = "202607", rankNo = 3, productId = 303L, score = 7.2)
            entityManager.persist(saved)
            entityManager.flush()
            entityManager.clear()

            val found = entityManager.find(ProductRankMonthlyMvEntity::class.java, saved.id)

            assertThat(found.periodKey).isEqualTo("202607")
            assertThat(found.rankNo).isEqualTo(3)
            assertThat(found.productId).isEqualTo(303L)
            assertThat(found.score).isEqualTo(7.2)
        }

        @Test
        fun `같은 (기간 키, 순위) 를 두 번 저장하면 제약 위반이다`() {
            entityManager.persist(ProductRankMonthlyMvEntity.of("202607", 1, 101L, 12.5))

            assertThrows<PersistenceException> {
                entityManager.persist(ProductRankMonthlyMvEntity.of("202607", 1, 202L, 10.0))
                entityManager.flush()
            }
        }
    }
}
