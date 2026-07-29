package com.loopers.domain.product

import com.loopers.support.event.DomainEvent
import com.loopers.support.event.ExternalEvent
import java.time.LocalDateTime
import java.util.UUID

sealed class ProductEvent : DomainEvent {
    abstract val productId: Long

    data class Viewed(
        override val productId: Long,
        val userId: Long?,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : ProductEvent()

    /**
     * 상품 삭제 사실 — 외부로 전파해 랭킹판 등 상품 파생 데이터를 정리하게 한다.
     */
    data class Deleted(
        override val productId: Long,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : ProductEvent(), ExternalEvent {
        override val eventType: String get() = "PRODUCT_DELETED"
        override val aggregateType: String get() = "PRODUCT"
        override val aggregateId: String get() = productId.toString()
    }
}
