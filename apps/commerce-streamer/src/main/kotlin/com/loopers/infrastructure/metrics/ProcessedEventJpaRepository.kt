package com.loopers.infrastructure.metrics

import org.springframework.data.jpa.repository.JpaRepository

interface ProcessedEventJpaRepository : JpaRepository<ProcessedEventEntity, Long> {
    fun existsByEventId(eventId: String): Boolean
}
