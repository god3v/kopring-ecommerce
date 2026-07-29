package com.loopers.domain.queue

/**
 * 순번 구간별 폴링 주기 정책 — 순수 함수. 서버가 다음 조회까지의 대기 시간을 정해 내려준다.
 * 가까운 순번일수록 짧게 조회해 입장 인지 지연을 줄이고, 먼 순번은 길게 조회해 폴링 부하를 줄인다.
 * 순번이 없으면(토큰 발급 완료 또는 미진입) 0 — 폴링을 끝내고 다음 행동으로 넘어가라는 신호다.
 */
object PollingIntervalPolicy {
    private const val NEAR_FRONT_BOUND = 100L
    private const val MID_QUEUE_BOUND = 1_000L

    fun intervalSeconds(position: Long?): Long = when {
        position == null -> 0L
        position < NEAR_FRONT_BOUND -> 1L
        position < MID_QUEUE_BOUND -> 3L
        else -> 5L
    }
}
