package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProductHourlyMetrics
import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

// 트랜잭션 롤백으로 격리한다 — 네이티브 upsert·bulk 삭제가 트랜잭션을 요구하기도 한다.
@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
@Transactional
class ProductHourlyMetricsRepositoryIntegrationTest @Autowired constructor(
    private val repository: ProductHourlyMetricsRepository,
    private val jpaRepository: ProductHourlyMetricsJpaRepository,
) {
    private fun delta(productId: Long, occurredAt: LocalDateTime, build: ProductHourlyMetrics.() -> Unit = {}): ProductHourlyMetrics =
        ProductHourlyMetrics.create(productId, occurredAt).apply(build)

    @DisplayName("누적하면,")
    @Nested
    inner class Accumulate {
        @Test
        fun `(상품, 버킷) 한 행에 저장되고 같은 버킷 재누적은 합산된다`() {
            val at = LocalDateTime.of(2026, 7, 16, 10, 37)

            repository.accumulate(delta(101L, at) { increaseView() })
            repository.accumulate(
                delta(101L, at) {
                increaseView()
                increaseLike()
            },
            )
            repository.accumulate(delta(101L, at) { addOrderQuantity(3) })

            val rows = jpaRepository.findAll()
            assertThat(rows).hasSize(1)
            with(rows.single()) {
                assertThat(productId).isEqualTo(101L)
                assertThat(statHour).isEqualTo(LocalDateTime.of(2026, 7, 16, 10, 0))
                assertThat(viewCount).isEqualTo(2L)
                assertThat(likeCount).isEqualTo(1L)
                assertThat(orderQuantity).isEqualTo(3L)
            }
        }

        @Test
        fun `서로 다른 시간 버킷의 수치는 섞이지 않는다`() {
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 16, 10, 59)) { increaseView() })
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 16, 11, 0)) { increaseView() })

            val rows = jpaRepository.findAll().sortedBy { it.statHour }
            assertThat(rows).hasSize(2)
            assertThat(rows.map { it.statHour }).containsExactly(
                LocalDateTime.of(2026, 7, 16, 10, 0),
                LocalDateTime.of(2026, 7, 16, 11, 0),
            )
            assertThat(rows.map { it.viewCount }).containsExactly(1L, 1L)
        }
    }

    @DisplayName("날짜로 합계를 조회하면,")
    @Nested
    inner class SumByDate {
        @Test
        fun `그 날짜(KST) 버킷들의 상품별 신호 합계가 반환되고 이웃 날짜는 제외된다`() {
            val date = LocalDate.of(2026, 7, 16)
            // 경계 안: 00:00(시작 포함) ~ 23:xx
            repository.accumulate(
                delta(101L, LocalDateTime.of(2026, 7, 16, 0, 0)) {
                increaseView()
                increaseLike()
            },
            )
            repository.accumulate(
                delta(101L, LocalDateTime.of(2026, 7, 16, 23, 59)) {
                increaseView()
                addOrderQuantity(2)
            },
            )
            repository.accumulate(delta(202L, LocalDateTime.of(2026, 7, 16, 12, 0)) { decreaseLike() })
            // 경계 밖: 전날 마지막 시각, 다음 날 자정
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 15, 23, 59)) { increaseView() })
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 17, 0, 0)) { increaseView() })

            val summaries = repository.sumByDate(date).associateBy { it.productId }

            assertThat(summaries).hasSize(2)
            with(summaries.getValue(101L)) {
                assertThat(viewCount).isEqualTo(2L)
                assertThat(likeCount).isEqualTo(1L)
                assertThat(orderQuantity).isEqualTo(2L)
            }
            // 순증 좋아요는 음수 그대로 합산된다 — 재계산이 증분 경로와 동치가 되는 근거.
            assertThat(summaries.getValue(202L).likeCount).isEqualTo(-1L)
        }
    }

    @DisplayName("상품의 집계 행을 지우면,")
    @Nested
    inner class RemoveByProductId {
        @Test
        fun `그 상품의 모든 버킷이 사라지고 재실행해도 결과가 같다`() {
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 16, 10, 0)) { increaseView() })
            repository.accumulate(delta(101L, LocalDateTime.of(2026, 7, 15, 10, 0)) { increaseView() })
            repository.accumulate(delta(202L, LocalDateTime.of(2026, 7, 16, 10, 0)) { increaseView() })

            repository.removeByProductId(101L)
            repository.removeByProductId(101L)

            val remaining = jpaRepository.findAll()
            assertThat(remaining).hasSize(1)
            assertThat(remaining.single().productId).isEqualTo(202L)
        }
    }
}
