package com.loopers.domain.queue

import java.util.UUID

/**
 * 주문 API 입장 토큰. 대기열에서 입장 처리된 유저에게 발급되는 불투명 문자열.
 * 만료는 저장소 TTL 이 담당하므로, 토큰 자체는 값만 가진다.
 */
data class EntryToken(val value: String) {
    companion object {
        fun issue(): EntryToken = EntryToken(UUID.randomUUID().toString())
    }
}
