package com.loopers.domain.ranking

/**
 * 행동 신호별 가중치. 조회·좋아요·주문은 스케일이 달라 단일 점수로 합치려면 서로 다른 가중치를 곱한다.
 * 값은 서비스 전략(무엇을 인기로 볼지)의 표현이라 설정으로 주입한다.
 */
data class RankingWeights(
    val view: Double,
    val like: Double,
    val order: Double,
)
