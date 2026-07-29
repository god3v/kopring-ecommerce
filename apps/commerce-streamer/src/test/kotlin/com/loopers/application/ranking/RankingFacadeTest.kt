package com.loopers.application.ranking

import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.metrics.ProductSignalSummary
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingSignal
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.abs

class RankingFacadeTest {
    private val rankingRepository = mockk<RankingRepository>(relaxed = true)
    private val productHourlyMetricsRepository = mockk<ProductHourlyMetricsRepository>()
    private val facade = RankingFacade(
        rankingRepository = rankingRepository,
        productHourlyMetricsRepository = productHourlyMetricsRepository,
        properties = RankingProperties(),
    )

    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)

    @DisplayName("신호를 반영하면,")
    @Nested
    inner class Reflect {
        @Test
        fun `조회 신호는 발생 시각 날짜의 랭킹판에 조회 가중치만큼 누적된다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.VIEW, productId = 101L, quantity = 1, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, 0.1, 172_800L) }
        }

        @Test
        fun `좋아요 취소 신호는 좋아요 가중치만큼 차감한다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.LIKE_CANCEL, productId = 101L, quantity = 1, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, -0.2, 172_800L) }
        }

        @Test
        fun `주문 신호는 수량 곱하기 주문 가중치만큼 누적한다`() {
            val eventId = UUID.randomUUID()

            facade.reflect(eventId, RankingSignal.ORDER, productId = 101L, quantity = 3, occurredAt = occurredAt)

            verify { rankingRepository.incrementScoreOnce(eventId, "rank:all:20260714", 101L, 0.7 * 3, 172_800L) }
        }
    }

    @DisplayName("이월을 실행하면,")
    @Nested
    inner class CarryOverToTomorrow {
        @Test
        fun `오늘 판을 소스로, 내일 판을 목적지로, 이월 가중치와 보존 기간을 저장소에 위임한다`() {
            facade.carryOverToTomorrow(LocalDate.of(2026, 7, 14))

            verify { rankingRepository.carryOver("rank:all:20260714", "rank:all:20260715", 0.1, 172_800L) }
        }
    }

    @DisplayName("재구축하면,")
    @Nested
    inner class Rebuild {
        private val date = LocalDate.of(2026, 7, 16)

        @Test
        fun `날짜의 신호 합계에 가중치를 적용해 판을 다시 만든다`() {
            every { productHourlyMetricsRepository.sumByDate(date) } returns listOf(
                ProductSignalSummary(productId = 101L, viewCount = 10, likeCount = 2, orderQuantity = 3),
            )
            every { productHourlyMetricsRepository.sumByDate(date.minusDays(1)) } returns emptyList()

            facade.rebuild(date)

            // view 10×0.1 + like 2×0.2 + order 3×0.7 = 3.5
            verify {
                rankingRepository.rebuild(
                    "rank:all:20260716",
                    match { entries -> entries.single().productId == 101L && abs(entries.single().score - 3.5) < 1e-9 },
                    172_800L,
                )
            }
        }

        @Test
        fun `전일 신호로 계산한 이월분이 더해진다 - 원 이월 시드의 근사 복원`() {
            every { productHourlyMetricsRepository.sumByDate(date) } returns listOf(
                ProductSignalSummary(productId = 101L, viewCount = 10, likeCount = 0, orderQuantity = 0),
            )
            every { productHourlyMetricsRepository.sumByDate(date.minusDays(1)) } returns listOf(
                ProductSignalSummary(productId = 101L, viewCount = 0, likeCount = 0, orderQuantity = 10),
                ProductSignalSummary(productId = 202L, viewCount = 0, likeCount = 10, orderQuantity = 0),
            )

            facade.rebuild(date)

            // 101: 오늘 1.0 + 전일 7.0×0.1 = 1.7 / 202: 오늘 없음 + 전일 2.0×0.1 = 0.2
            verify {
                rankingRepository.rebuild(
                    "rank:all:20260716",
                    match { entries ->
                        entries.size == 2 &&
                            entries.any { it.productId == 101L && abs(it.score - 1.7) < 1e-9 } &&
                            entries.any { it.productId == 202L && abs(it.score - 0.2) < 1e-9 }
                    },
                    172_800L,
                )
            }
        }
    }

    @DisplayName("자가 복구 점검은,")
    @Nested
    inner class RecoverIfLost {
        private val today = LocalDate.of(2026, 7, 16)
        private val todayKey = "rank:all:20260716"

        @Test
        fun `오늘 판이 이미 있으면 아무것도 하지 않는다`() {
            every { rankingRepository.exists(todayKey) } returns true

            facade.recoverIfLost(today)

            verify(exactly = 0) { rankingRepository.rebuild(any(), any(), any()) }
        }

        @Test
        fun `판이 없고 오늘 집계가 있으면 재구축한다 - 유실 신호`() {
            every { rankingRepository.exists(todayKey) } returns false
            every { productHourlyMetricsRepository.sumByDate(today) } returns listOf(
                ProductSignalSummary(productId = 101L, viewCount = 1, likeCount = 0, orderQuantity = 0),
            )
            every { productHourlyMetricsRepository.sumByDate(today.minusDays(1)) } returns emptyList()

            facade.recoverIfLost(today)

            verify { rankingRepository.rebuild(todayKey, any(), any()) }
        }

        @Test
        fun `판도 오늘 집계도 없으면 재구축하지 않는다 - 정상적인 무활동`() {
            every { rankingRepository.exists(todayKey) } returns false
            every { productHourlyMetricsRepository.sumByDate(today) } returns emptyList()

            facade.recoverIfLost(today)

            verify(exactly = 0) { rankingRepository.rebuild(any(), any(), any()) }
        }
    }

    @DisplayName("상품 삭제를 반영하면,")
    @Nested
    inner class RemoveProduct {
        @Test
        fun `보존 기간 안의 모든 일간 랭킹판에서 그 상품을 제거한다`() {
            // ttl 48h 기준: 이월로 미리 생성된 내일 판 ~ 보존 범위 과거까지 훑는다(하드코딩 '오늘·어제' 아님).
            facade.removeProduct(productId = 101L, occurredAt = occurredAt)

            verify {
                rankingRepository.removeProduct(
                    listOf("rank:all:20260715", "rank:all:20260714", "rank:all:20260713", "rank:all:20260712"),
                    101L,
                )
            }
        }
    }
}
