package com.loopers.infrastructure.queue

import com.loopers.application.queue.QueueFacade
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 입장 처리 스케줄러 — 일정 주기로 대기열 앞에서 N명을 꺼내 입장 토큰을 발급한다.
 * 배치 크기·주기는 하류(DB·PG) 처리량에 맞춰 산정한다(goal.md 처리량 설계 기준 참고).
 * 테스트 프로필에서는 대기열 상태를 흔들지 않도록 끈다 — 테스트는 `QueueFacade.admit` 를 직접 호출한다.
 */
@Component
@Profile("!test")
class QueueAdmissionScheduler(
    private val queueFacade: QueueFacade,
    @Value("\${loopers.queue.admit-batch-size:8}")
    private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${loopers.queue.admit-interval-ms:100}")
    fun admit() {
        val issued = queueFacade.admit(batchSize)
        if (issued > 0) {
            log.debug("입장 토큰 {}건 발급", issued)
        }
    }
}
