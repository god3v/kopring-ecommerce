package com.loopers.support.event

import java.time.LocalDateTime
import java.util.UUID

interface DomainEvent {
    val eventId: UUID
    val occurredAt: LocalDateTime
}
