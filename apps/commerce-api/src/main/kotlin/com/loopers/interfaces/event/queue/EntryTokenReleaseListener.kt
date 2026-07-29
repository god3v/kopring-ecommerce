package com.loopers.interfaces.event.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.domain.order.OrderEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 주문 성공(커밋) 이후 입장 토큰을 회수한다.
 * OrderEvent.Created 를 AFTER_COMMIT 로 구독 — 커밋 성공 시점에 정확히 걸리고, HTTP 상태·주문 로직과 결합하지 않는다.
 * 회수(Redis DEL)는 저장소가 장애를 흡수하므로(fail-closed no-op) 커밋된 주문에 영향을 주지 않는다.
 */
@Component
class EntryTokenReleaseListener(
    private val queueFacade: QueueFacade,
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: OrderEvent.Created) {
        queueFacade.leave(event.userId)
    }
}
