package com.loopers.interfaces.event

import com.loopers.support.event.DomainEvent
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class DomainEventLoggingListener {
    private val log = LoggerFactory.getLogger(DomainEventLoggingListener::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: DomainEvent) {
        log.info(
            "[domain-event] type={} eventId={} occurredAt={}",
            event.javaClass.typeName,
            event.eventId,
            event.occurredAt,
        )
    }
}
