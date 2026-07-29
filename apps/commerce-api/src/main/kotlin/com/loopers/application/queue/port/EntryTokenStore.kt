package com.loopers.application.queue.port

import com.loopers.domain.queue.EntryToken
import java.time.Duration

/**
 * 입장 토큰 저장소 (outbound port).
 * userId 당 토큰 하나를 TTL 과 함께 보관한다. TTL 이 지나면 자동 만료된다.
 */
interface EntryTokenStore {
    /** userId 에 토큰을 TTL 과 함께 발급(덮어쓰기)한다. */
    fun issue(userId: Long, token: EntryToken, ttl: Duration)

    /** userId 의 현재 토큰. 없거나 만료됐으면 null. */
    fun find(userId: Long): EntryToken?

    /** userId 의 토큰을 제거한다(주문 완료 후 회수). */
    fun remove(userId: Long)
}
