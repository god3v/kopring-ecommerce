package com.loopers.domain.order

import com.loopers.support.page.PageResult
import java.time.LocalDateTime

interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: Long): Order?

    /** 결제 처리(상태 전이) 경로 전용 — 비관적 쓰기 락으로 조회한다. 동시 pay 직렬화로 이중 청구·이중 보상 방지. */
    fun findByIdForUpdate(id: Long): Order?

    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): Order?

    // start/end 는 각각 독립적으로 선택적이다. null 인 경계는 필터에서 제외된다
    // (둘 다 null 이면 회원의 전체 주문). 무한 sentinel 시각을 넘기지 않는다.
    fun findAllByUserIdInPeriod(
        userId: Long,
        start: LocalDateTime?,
        end: LocalDateTime?,
        page: Int,
        size: Int,
    ): PageResult<Order>

    fun findAllForAdmin(page: Int, size: Int): PageResult<Order>
}
