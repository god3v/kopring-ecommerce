package com.loopers.domain.metrics

/**
 * 상품 조회 전용 집계. 좋아요·판매·조회 이벤트를 증분으로 누적한다.
 * 이벤트는 절대값이 아닌 사실(생성/취소/판매/조회)을 나르므로 순서와 무관하게 교환법칙이 성립하고,
 * 중복은 상위(멱등 처리)에서 막는다 — 그래서 여기서는 단순 증감만 한다.
 */
class ProductMetrics private constructor(
    val productId: Long,
    likeCount: Long,
    salesCount: Long,
    viewCount: Long,
    deleted: Boolean,
) {
    var likeCount: Long = likeCount
        private set
    var salesCount: Long = salesCount
        private set
    var viewCount: Long = viewCount
        private set

    // 삭제 표식(tombstone). 삭제 뒤 도착한 지연 신호가 랭킹 원본(시간별 집계)을 되살리지 않게 거른다.
    // 누적 지표 자체는 삭제 후에도 계속 쌓인다 — 실제 발생한 사실의 분석 이력.
    var deleted: Boolean = deleted
        private set

    fun markDeleted() {
        deleted = true
    }

    fun increaseLike() {
        likeCount += 1
    }

    fun decreaseLike() {
        likeCount = (likeCount - 1).coerceAtLeast(0)
    }

    fun addSales(quantity: Int) {
        salesCount += quantity
    }

    fun increaseView() {
        viewCount += 1
    }

    companion object {
        fun create(productId: Long): ProductMetrics =
            ProductMetrics(productId = productId, likeCount = 0, salesCount = 0, viewCount = 0, deleted = false)

        fun of(productId: Long, likeCount: Long, salesCount: Long, viewCount: Long, deleted: Boolean): ProductMetrics =
            ProductMetrics(productId, likeCount, salesCount, viewCount, deleted)
    }
}
