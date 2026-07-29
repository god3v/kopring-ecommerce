package com.loopers.infrastructure.outbox

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Outbox 릴레이 주기 트리거 — 미발행(PENDING) 아웃박스를 주기적으로 발행하도록 [OutboxRelay] 를 구동한다.
 * "언제 도는가"(트리거)와 "무엇을 하는가"(릴레이)를 분리해, 통합 테스트가 relay() 를 직접 호출해 발행을 명시 제어할 수 있게 한다.
 *
 * `test` 프로파일에서는 비활성화한다. 통합 테스트들은 하나의 공유 DB(outbox 테이블)를 쓰고 컨텍스트가 캐시되어 살아있으므로,
 * 백그라운드 릴레이가 켜져 있으면 다른 컨텍스트의 스케줄러가 릴레이 통합 테스트가 만든 PENDING 행을 먼저 발행해버려 플레이키해진다.
 */
@Component
@Profile("!test")
class OutboxRelayScheduler(
    private val relay: OutboxRelay,
) {
    @Scheduled(fixedDelayString = "\${loopers.outbox.relay-interval-ms:1000}")
    fun tick() = relay.relay()
}
