package com.loopers.infrastructure.metrics

import com.loopers.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.util.UUID

/**
 * 소비 멱등 기록. event_id 를 유니크로 두어 이미 처리한 이벤트의 재소비를 걸러낸다.
 * 집계 upsert 와 같은 트랜잭션에서 기록되므로, 처리 성공 뒤에만 멱등 표식이 남는다.
 */
@Entity
@Table(name = "event_handled")
class ProcessedEventEntity private constructor(
    eventId: String,
) : BaseEntity() {
    @Column(name = "event_id", nullable = false, unique = true, updatable = false, length = 36)
    var eventId: String = eventId
        protected set

    companion object {
        fun of(eventId: UUID): ProcessedEventEntity = ProcessedEventEntity(eventId = eventId.toString())
    }
}
