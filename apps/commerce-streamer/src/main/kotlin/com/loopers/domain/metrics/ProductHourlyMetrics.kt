package com.loopers.domain.metrics

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 시간 버킷 단위 상품 신호 집계 — 랭킹 재계산의 원본(RDB SoT).
 * 발생 시각을 정시로 절단해 (상품, 버킷) 한 행에 신호 개수를 누적한다.
 * 가중 점수가 아니라 신호 개수를 저장한다 — 가중치는 재계산 시점에 적용해, 가중치가 바뀌어도 원본이 유효하다.
 */
class ProductHourlyMetrics private constructor(
    val productId: Long,
    val statHour: LocalDateTime,
    viewCount: Long,
    likeCount: Long,
    orderQuantity: Long,
) {
    var viewCount: Long = viewCount
        private set

    // 순증(생성-취소). 버킷을 넘는 취소가 있으면 음수가 될 수 있다 — 누적판과 달리 0 으로 자르지 않아야
    // 버킷 합산 재계산이 랭킹판 증분 경로와 동치가 된다.
    var likeCount: Long = likeCount
        private set

    var orderQuantity: Long = orderQuantity
        private set

    fun increaseView() {
        viewCount += 1
    }

    fun increaseLike() {
        likeCount += 1
    }

    fun decreaseLike() {
        likeCount -= 1
    }

    fun addOrderQuantity(quantity: Int) {
        orderQuantity += quantity
    }

    companion object {
        fun create(productId: Long, occurredAt: LocalDateTime): ProductHourlyMetrics = ProductHourlyMetrics(
            productId = productId,
            statHour = occurredAt.truncatedTo(ChronoUnit.HOURS),
            viewCount = 0,
            likeCount = 0,
            orderQuantity = 0,
        )
    }
}
