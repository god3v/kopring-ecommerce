package com.loopers.support.event

/**
 * 시스템 경계를 넘어 외부(Kafka)로 전파되어야 하는 도메인 이벤트 마커.
 * Outbox 브리지는 이 타입만 구독한다 — 순수 JVM 내부(로깅 등) 이벤트는 `DomainEvent` 만 구현해 외부로 나가지 않는다.
 *
 * 이벤트가 자신의 aggregate 정체(종류·식별키)와 논리 타입을 스스로 밝힌다 — 아웃박스 적재·파티션 키·봉투 구성에 쓰인다.
 * 토픽 이름 같은 전송 세부는 여기서 알지 않는다(릴레이가 aggregateType 으로 매핑).
 *
 * `eventType` 은 안정적 논리 타입 문자열(예: ORDER_CREATED). Consumer 가 이 값으로 처리 분기하므로,
 * 클래스명(FQCN)이 아니라 리팩터링에 견디는 계약 문자열로 노출한다.
 */
interface ExternalEvent : DomainEvent {
    val aggregateType: String
    val aggregateId: String
    val eventType: String
}
