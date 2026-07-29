package com.loopers.domain.order

import com.loopers.support.event.DomainEvent
import com.loopers.support.event.ExternalEvent
import java.time.LocalDateTime
import java.util.UUID

/**
 * 주문 도메인 이벤트 — 발생한 사실을 타입으로 구분한다.
 * 핵심 주문 트랜잭션과 분리된 부가 처리(토큰 회수·판매량 집계 등)가 이 이벤트를 소비한다.
 * 외부(Kafka) 전파 여부는 사실 단위로 갈린다 — 외부 소비자가 있는 이벤트만 `ExternalEvent` 를 구현한다.
 */
sealed class OrderEvent : DomainEvent {
    abstract val orderId: Long

    /** 주문 라인 스냅샷 — 이벤트 payload 공용(어떤 상품이 몇 개인가). */
    data class Line(val productId: Long, val quantity: Int)

    /**
     * 주문 생성 사실 — 내부 소비자(입장 토큰 회수)용. 결제 미확정 주문이라 외부(판매 집계·랭킹)로는 전파하지 않는다.
     */
    data class Created(
        override val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
        val lines: List<Line>,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : OrderEvent() {
        companion object {
            fun from(order: Order): Created = Created(
                orderId = order.id,
                userId = order.userId,
                totalAmount = order.totalAmount,
                lines = order.lines.map { Line(productId = it.productId, quantity = it.quantity.value) },
            )
        }
    }

    /**
     * 결제 확정 사실 — 판매량 집계·랭킹 등 "실제 판매"를 소비하는 외부 소비자용.
     * 주문 생성(Created)과 달리 결제까지 완료된 주문만 나른다.
     */
    data class Paid(
        override val orderId: Long,
        val userId: Long,
        val totalAmount: Long,
        val lines: List<Line>,
        override val eventId: UUID = UUID.randomUUID(),
        override val occurredAt: LocalDateTime = LocalDateTime.now(),
    ) : OrderEvent(), ExternalEvent {
        override val eventType: String get() = "ORDER_PAID"
        override val aggregateType: String get() = "ORDER"
        override val aggregateId: String get() = orderId.toString()

        companion object {
            fun from(order: Order): Paid = Paid(
                orderId = order.id,
                userId = order.userId,
                totalAmount = order.totalAmount,
                lines = order.lines.map { Line(productId = it.productId, quantity = it.quantity.value) },
            )
        }
    }
}
