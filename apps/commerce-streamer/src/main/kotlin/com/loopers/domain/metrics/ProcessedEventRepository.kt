package com.loopers.domain.metrics

import java.util.UUID

/**
 * 소비 멱등 저장소. 이미 처리한 이벤트(eventId)를 기록해 중복 소비를 걸러낸다.
 * event_id 를 로그가 아닌 별도 핸들링 테이블로 두는 이유는 "처리 여부 판정"이 조회·집계 로그와 다른 관심사이기 때문이다.
 */
interface ProcessedEventRepository {
    fun existsByEventId(eventId: UUID): Boolean

    fun save(eventId: UUID)
}
