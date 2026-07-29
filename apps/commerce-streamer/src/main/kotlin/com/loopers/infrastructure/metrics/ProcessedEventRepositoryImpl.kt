package com.loopers.infrastructure.metrics

import com.loopers.domain.metrics.ProcessedEventRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class ProcessedEventRepositoryImpl(
    private val processedEventJpaRepository: ProcessedEventJpaRepository,
) : ProcessedEventRepository {
    override fun existsByEventId(eventId: UUID): Boolean =
        processedEventJpaRepository.existsByEventId(eventId.toString())

    override fun save(eventId: UUID) {
        processedEventJpaRepository.save(ProcessedEventEntity.of(eventId))
    }
}
