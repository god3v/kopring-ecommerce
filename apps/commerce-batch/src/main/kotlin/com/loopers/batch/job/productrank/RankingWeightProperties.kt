package com.loopers.batch.job.productrank

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 랭킹 점수 가중치 — 신호 개수에 곱해 단일 점수로 합친다.
 * streamer 의 실시간 랭킹과 같은 값을 유지해야 일간과 주간·월간의 순위 산식이 일치한다 — 바꾸면 두 앱을 함께 바꾼다.
 */
@ConfigurationProperties(prefix = "loopers.ranking.weight")
data class RankingWeightProperties(
    val view: Double = 0.1,
    val like: Double = 0.2,
    val order: Double = 0.7,
)
