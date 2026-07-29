package com.loopers.application.ranking

import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.domain.ranking.RankingScorePolicy
import com.loopers.domain.ranking.RankingSignal
import com.loopers.domain.ranking.RankingWeights
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.ceil

/**
 * 행동 신호를 랭킹 점수 반영으로 조율한다 — 발생 시각으로 랭킹판 키를, 신호·수량으로 가중 점수를 정해 저장소에 넘긴다.
 * 가중치·보존 기간은 설정(RankingProperties)으로 외부화한다. 순수 정책(RankingScorePolicy)은 빈이 아니라 여기서 직접 만든다.
 */
@Component
class RankingFacade(
    private val rankingRepository: RankingRepository,
    private val productHourlyMetricsRepository: ProductHourlyMetricsRepository,
    properties: RankingProperties,
) {
    private val scorePolicy = RankingScorePolicy(
        RankingWeights(properties.weight.view, properties.weight.like, properties.weight.order),
    )
    private val ttlSeconds = properties.keyTtlHours * SECONDS_PER_HOUR
    private val carryOverWeight = properties.carryOver.weight

    // 보존 기간으로부터 살아 있을 수 있는 일간 판 수를 도출한다 — TTL 이 바뀌어도 삭제 정리가 따라간다.
    private val retentionDays = ceil(properties.keyTtlHours / HOURS_PER_DAY).toInt()

    fun reflect(eventId: UUID, signal: RankingSignal, productId: Long, quantity: Int, occurredAt: LocalDateTime) {
        val delta = scorePolicy.scoreOf(signal, quantity)
        rankingRepository.incrementScoreOnce(eventId, RankingKey.of(occurredAt), productId, delta, ttlSeconds)
    }

    /**
     * 오늘 판 점수 일부를 내일 판의 출발점으로 복사한다 — 자정 직후 랭킹판이 비는 콜드 스타트를 완화한다.
     * 내일 판이 이미 있으면 저장소가 건너뛰므로, 중복 실행이 실점수를 덮어쓰지 않는다.
     */
    fun carryOverToTomorrow(today: LocalDate) {
        rankingRepository.carryOver(RankingKey.of(today), RankingKey.of(today.plusDays(1)), carryOverWeight, ttlSeconds)
    }

    /**
     * 랭킹판을 시간별 집계(RDB SoT)로부터 다시 만든다 — Redis 유실 복구.
     * 그 날짜 신호 합계에 가중치를 적용해 점수를 재계산하고, 원 이월(23:50 잡)이 넣었을
     * 전일 시드도 전일 신호로 재계산해 더한 뒤 판을 원자 교체한다.
     */
    fun rebuild(date: LocalDate) {
        val scores = linkedMapOf<Long, Double>()
        productHourlyMetricsRepository.sumByDate(date).forEach {
            scores[it.productId] = scorePolicy.totalScoreOf(it.viewCount, it.likeCount, it.orderQuantity)
        }
        productHourlyMetricsRepository.sumByDate(date.minusDays(1)).forEach {
            val seed = scorePolicy.totalScoreOf(it.viewCount, it.likeCount, it.orderQuantity) * carryOverWeight
            scores.merge(it.productId, seed, Double::plus)
        }
        val entries = scores.map { (productId, score) -> RankedEntry(productId, score) }
        rankingRepository.rebuild(RankingKey.of(date), entries, ttlSeconds)
    }

    /**
     * 오늘 판 유실을 감지하면 재구축한다 — "오늘 집계는 있는데 판이 없다"만 유실로 본다.
     * 판이 살아 있거나, 집계도 없는 무활동 날이면 아무것도 하지 않는다.
     */
    fun recoverIfLost(today: LocalDate) {
        if (rankingRepository.exists(RankingKey.of(today))) return
        if (productHourlyMetricsRepository.sumByDate(today).isEmpty()) return
        rebuild(today)
    }

    /**
     * 삭제된 상품을 살아 있을 수 있는 모든 일간 랭킹판에서 걷어낸다.
     * 판 범위는 보존 기간(ttl)에서 도출하고, 이월로 미리 생성된 내일 판까지 포함한다 — 특정 TTL 값에 하드코딩되지 않는다.
     */
    fun removeProduct(productId: Long, occurredAt: LocalDateTime) {
        val today = occurredAt.toLocalDate()
        val keys = (CARRY_OVER_LOOKAHEAD_DAYS downTo -retentionDays)
            .map { RankingKey.of(today.plusDays(it.toLong())) }
        rankingRepository.removeProduct(keys, productId)
    }

    companion object {
        private const val SECONDS_PER_HOUR = 3_600L
        private const val HOURS_PER_DAY = 24.0
        private const val CARRY_OVER_LOOKAHEAD_DAYS = 1
    }
}
