package com.loopers.kafka

/**
 * 형식이 깨져 재전달해도 영영 실패하는 메시지(역직렬화 불가·필수 필드 누락) — 재시도 없이 곧장 DLT 로 보내는 신호.
 * KafkaConfig 의 에러 핸들러가 비재시도 예외로 분류한다.
 */
class MalformedEventException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
