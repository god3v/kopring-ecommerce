package com.loopers.application.queue.port

import java.time.Instant

/**
 * 주문 대기열 (outbound port).
 * 진입 순서 보장·중복 방지·순번 조회를 Redis Sorted Set 원자 연산으로 제공한다.
 */
interface WaitingQueueRepository {
    /** 대기열에 진입한다. 이미 있으면 기존 순서를 보존하고, 새로 추가되면 true 를 반환한다. */
    fun enter(userId: Long, at: Instant): Boolean

    /** 0-based 순번. 대기열에 없으면 null. */
    fun rank(userId: Long): Long?

    /** 전체 대기 인원. */
    fun size(): Long

    /** 앞에서부터 최대 count 명을 꺼내 반환한다(대기열에서 제거). 스케줄러의 입장 처리에 쓴다. */
    fun pollNext(count: Long): List<Long>
}
