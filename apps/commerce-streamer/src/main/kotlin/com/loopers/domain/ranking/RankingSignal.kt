package com.loopers.domain.ranking

/**
 * 랭킹 점수의 원천이 되는 유저 행동 종류. 이벤트 종류를 도메인 어휘로 옮긴 것.
 */
enum class RankingSignal {
    VIEW,
    LIKE,
    LIKE_CANCEL,
    ORDER,
}
