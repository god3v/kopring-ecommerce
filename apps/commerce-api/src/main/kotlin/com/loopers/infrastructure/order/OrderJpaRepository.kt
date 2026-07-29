package com.loopers.infrastructure.order

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface OrderJpaRepository : JpaRepository<OrderEntity, Long> {
    fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): OrderEntity?

    // 결제 처리 경로 전용 — 비관 쓰기 락. 동시 pay 의 상태 전이를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM OrderEntity o WHERE o.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): OrderEntity?

    // 기간 경계가 null 이면 해당 조건을 건너뛴다 — sentinel 시각을 넣지 않아 범위 밖 값으로 매칭이 비는 문제를 피한다. 경계는 inclusive.
    @Query(
        value = """
        SELECT o FROM OrderEntity o
        WHERE o.userId = :userId
          AND (:start IS NULL OR o.orderedAt >= :start)
          AND (:end IS NULL OR o.orderedAt <= :end)
        """,
        countQuery = """
        SELECT COUNT(o) FROM OrderEntity o
        WHERE o.userId = :userId
          AND (:start IS NULL OR o.orderedAt >= :start)
          AND (:end IS NULL OR o.orderedAt <= :end)
        """,
    )
    fun findAllByUserIdInPeriod(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDateTime?,
        @Param("end") end: LocalDateTime?,
        pageable: Pageable,
    ): Page<OrderEntity>

    fun findAllBy(pageable: Pageable): Page<OrderEntity>
}
