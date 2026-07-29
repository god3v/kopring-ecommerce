package com.loopers.infrastructure.outbox

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface OutboxEventJpaRepository : JpaRepository<OutboxEventEntity, Long> {
    fun findByStatusOrderByIdAsc(status: OutboxStatus): List<OutboxEventEntity>

    // 릴레이 1회분 상한 조회 — 미발행이 쌓여도 한 주기의 트랜잭션·발행 시간을 배치 크기로 묶는다.
    fun findByStatusOrderByIdAsc(status: OutboxStatus, pageable: Pageable): List<OutboxEventEntity>
}
