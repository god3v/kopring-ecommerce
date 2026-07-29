package com.loopers.application.ranking

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 랭킹 정책 설정 — 신호별 가중치와 랭킹판 보존 기간. 서비스 전략에 따라 조정한다.
 */
@ConfigurationProperties(value = "loopers.ranking")
data class RankingProperties(
    val weight: Weight = Weight(),
    val keyTtlHours: Long = 48,
    val carryOver: CarryOver = CarryOver(),
) {
    data class Weight(
        val view: Double = 0.1,
        val like: Double = 0.2,
        val order: Double = 0.7,
    )

    // 이월 가중치 — 전일 상위권이 오늘 초반 실점수를 가리지 않을 만큼 작게, 빈 판은 면할 만큼.
    data class CarryOver(
        val weight: Double = 0.1,
    )
}
