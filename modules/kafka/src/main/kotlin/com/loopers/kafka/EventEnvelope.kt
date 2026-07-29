package com.loopers.kafka

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDateTime

/**
 * Kafka 로 전파되는 이벤트의 와이어 계약(봉투). 도메인 무관 — 두 앱(producer·consumer)이 공유하는 메시지 포맷.
 *
 * payload(도메인 이벤트 JSON) 만으로는 Consumer 가 종류(예: 좋아요 생성 vs 취소)를 구분할 수 없고,
 * 멱등(`eventId`)·낡은 이벤트 판별(`occurredAt`) 근거도 계약으로 보장되지 않는다. 봉투가 이를 자기서술한다.
 * `eventType` 은 안정적 논리 타입 문자열(예: ORDER_CREATED)로, 앱 간 클래스명 결합을 피한다.
 */
data class EventEnvelope(
    val eventId: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val occurredAt: LocalDateTime,
    val payload: JsonNode,
)
