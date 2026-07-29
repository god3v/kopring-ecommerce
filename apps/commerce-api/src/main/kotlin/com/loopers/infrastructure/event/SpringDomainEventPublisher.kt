package com.loopers.infrastructure.event

import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.support.event.DomainEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
class SpringDomainEventPublisher(
    private val delegate: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent) {
        delegate.publishEvent(event)
    }
}
