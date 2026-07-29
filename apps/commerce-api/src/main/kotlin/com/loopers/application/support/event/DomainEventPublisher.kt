package com.loopers.application.support.event

import com.loopers.support.event.DomainEvent

/**
 * 도메인 이벤트 발행 outbound port — Facade 는 이 포트만 알고, Spring `ApplicationEventPublisher` 의존은 어댑터에 격리한다.
 */
interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}
