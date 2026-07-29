package com.loopers.domain.ranking

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class RankingScorePolicyTest {
    private val policy = RankingScorePolicy(RankingWeights(view = 0.1, like = 0.2, order = 0.7))

    @Test
    fun `조회 신호 1건의 점수는 조회 가중치와 같다`() {
        assertThat(policy.scoreOf(RankingSignal.VIEW, quantity = 1)).isEqualTo(0.1)
    }

    @Test
    fun `좋아요 신호 1건의 점수는 좋아요 가중치와 같다`() {
        assertThat(policy.scoreOf(RankingSignal.LIKE, quantity = 1)).isEqualTo(0.2)
    }

    @Test
    fun `좋아요 취소 신호의 점수는 좋아요 가중치의 음수다`() {
        assertThat(policy.scoreOf(RankingSignal.LIKE_CANCEL, quantity = 1)).isEqualTo(-0.2)
    }

    @Test
    fun `주문 신호의 점수는 수량 곱하기 주문 가중치다`() {
        assertThat(policy.scoreOf(RankingSignal.ORDER, quantity = 3)).isCloseTo(2.1, within(1e-9))
    }

    @Test
    fun `주문 1건의 점수가 좋아요 3건의 합보다 크다`() {
        val order = policy.scoreOf(RankingSignal.ORDER, quantity = 1)
        val threeLikes = policy.scoreOf(RankingSignal.LIKE, quantity = 1) * 3

        assertThat(order).isGreaterThan(threeLikes)
    }
}
