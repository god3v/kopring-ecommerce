package com.loopers.infrastructure.outbox

/** 아웃박스 발행 상태 — 실패도 기록해 재시도/격리(DLQ) 판단의 근거로 삼는다. */
enum class OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
}
