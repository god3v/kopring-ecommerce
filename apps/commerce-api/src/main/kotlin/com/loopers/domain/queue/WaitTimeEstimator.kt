package com.loopers.domain.queue

import kotlin.math.ceil

/**
 * 예상 대기 시간 계산기 — 순수 함수. 내 순번 / 초당 처리량.
 * 추정값이라 올림한다. 순번 0(내 차례) 이거나 처리량이 0 이하면 0 을 반환한다.
 */
object WaitTimeEstimator {
    fun estimateSeconds(position: Long, throughputPerSecond: Double): Long {
        if (position <= 0L || throughputPerSecond <= 0.0) return 0L
        return ceil(position / throughputPerSecond).toLong()
    }
}
