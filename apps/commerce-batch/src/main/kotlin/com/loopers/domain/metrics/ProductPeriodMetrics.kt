package com.loopers.domain.metrics

/**
 * 기간(주/월) 단위 상품 신호 집계 — 시간 버킷 합산의 결과를 나른다.
 * 가중 점수가 아니라 신호 개수를 저장한다 — 가중치는 랭킹 산출 시점에 적용해, 가중치가 바뀌어도 원본이 유효하다.
 * 좋아요는 순증(생성-취소)이라 음수일 수 있다. 0 으로 자르지 않아야 버킷 합산이 증분 경로와 동치가 된다.
 */
class ProductPeriodMetrics private constructor(
    val productId: Long,
    val periodKey: String,
    val viewCount: Long,
    val likeCount: Long,
    val orderQuantity: Long,
) {
    companion object {
        fun of(
            productId: Long,
            periodKey: String,
            viewCount: Long,
            likeCount: Long,
            orderQuantity: Long,
        ): ProductPeriodMetrics = ProductPeriodMetrics(
            productId = productId,
            periodKey = periodKey,
            viewCount = viewCount,
            likeCount = likeCount,
            orderQuantity = orderQuantity,
        )
    }
}
