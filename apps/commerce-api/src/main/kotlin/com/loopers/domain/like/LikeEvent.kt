package com.loopers.domain.like

import com.loopers.support.event.ExternalEvent
import java.time.LocalDateTime
import java.util.UUID

sealed class LikeEvent : ExternalEvent {
    abstract val productId: Long
    override val aggregateType: String get() = "PRODUCT"
    override val aggregateId: String get() = productId.toString()

    data class Created(
        override val productId: Long,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : LikeEvent() {
        override val eventType: String get() = "LIKE_CREATED"
    }

    data class Canceled(
        override val productId: Long,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : LikeEvent() {
        override val eventType: String get() = "LIKE_CANCELED"
    }
}
