package com.loopers.domain.ranking

/**
 * 행동 신호와 수량을 가중 점수로 환산한다. 무상태 순수 정책 — 인자만으로 계산한다.
 */
class RankingScorePolicy(
    private val weights: RankingWeights,
) {
    fun scoreOf(signal: RankingSignal, quantity: Int): Double = scoreOf(signal, quantity.toLong())

    fun scoreOf(signal: RankingSignal, quantity: Long): Double = when (signal) {
        RankingSignal.VIEW -> weights.view * quantity
        RankingSignal.LIKE -> weights.like * quantity
        RankingSignal.LIKE_CANCEL -> -weights.like * quantity
        RankingSignal.ORDER -> weights.order * quantity
    }

    /**
     * 신호 합계(조회·순증 좋아요·주문 수량)를 하루치 총점으로 환산한다 — 재구축(재계산) 경로가 쓴다.
     * 좋아요는 순증이라 취소분이 이미 상쇄돼 있다. 음수 합계는 음수 점수 그대로 반영된다.
     */
    fun totalScoreOf(viewCount: Long, likeCount: Long, orderQuantity: Long): Double =
        scoreOf(RankingSignal.VIEW, viewCount) +
            scoreOf(RankingSignal.LIKE, likeCount) +
            scoreOf(RankingSignal.ORDER, orderQuantity)
}
