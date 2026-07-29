package com.loopers.application.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Instant

/**
 * QueueFacade.admit 를 실제 Redis 로 검증한다 — 대기열에서 꺼내 토큰을 발급하고,
 * 발급된 토큰으로 ensureAdmitted 가 통과하는지까지 확인한다.
 * 스케줄러(QueueAdmissionScheduler)는 test 프로필에서 꺼져 있으므로 대기열을 흔들지 않는다.
 */
@SpringBootTest
@Import(RedisTestContainersConfig::class)
@DisplayName("QueueFacade 통합")
class QueueFacadeIntegrationTest @Autowired constructor(
    private val queueFacade: QueueFacade,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenStore: EntryTokenStore,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("admit — 앞 N명에게 토큰을 발급하고 대기열에서 제거한다")
    fun admitIssuesTokensAndShrinksQueue() {
        waitingQueueRepository.enter(1L, Instant.ofEpochMilli(100))
        waitingQueueRepository.enter(2L, Instant.ofEpochMilli(200))
        waitingQueueRepository.enter(3L, Instant.ofEpochMilli(300))

        val issued = queueFacade.admit(2)

        assertThat(issued).isEqualTo(2)
        assertThat(entryTokenStore.find(1L)).isNotNull()
        assertThat(entryTokenStore.find(2L)).isNotNull()
        assertThat(entryTokenStore.find(3L)).isNull()
        assertThat(waitingQueueRepository.size()).isEqualTo(1L)
    }

    @Test
    @DisplayName("발급된 토큰으로 ensureAdmitted 가 통과한다")
    fun ensureAdmittedPassesWithIssuedToken() {
        waitingQueueRepository.enter(7L, Instant.ofEpochMilli(100))
        queueFacade.admit(1)

        val token = entryTokenStore.find(7L)!!

        queueFacade.ensureAdmitted(7L, token.value)
    }
}
