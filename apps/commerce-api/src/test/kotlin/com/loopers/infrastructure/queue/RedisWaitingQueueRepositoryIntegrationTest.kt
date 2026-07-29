package com.loopers.infrastructure.queue

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
 * 실제 Redis(Testcontainer) 로 Sorted Set 순서 보장·NX 중복 방지·순번 조회를 검증한다.
 */
@SpringBootTest
@Import(RedisTestContainersConfig::class)
@DisplayName("RedisWaitingQueueRepository")
class RedisWaitingQueueRepositoryIntegrationTest @Autowired constructor(
    private val waitingQueue: WaitingQueueRepository,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("먼저 진입한 순서대로 0-based 순번을 반환한다")
    fun ranksInEntryOrder() {
        waitingQueue.enter(1L, Instant.ofEpochMilli(100))
        waitingQueue.enter(2L, Instant.ofEpochMilli(200))
        waitingQueue.enter(3L, Instant.ofEpochMilli(300))

        assertThat(waitingQueue.rank(1L)).isEqualTo(0L)
        assertThat(waitingQueue.rank(2L)).isEqualTo(1L)
        assertThat(waitingQueue.rank(3L)).isEqualTo(2L)
    }

    @Test
    @DisplayName("이미 진입한 유저가 늦은 시각으로 재진입해도 기존 순번이 보존된다(NX)")
    fun preservesOrderOnReentry() {
        waitingQueue.enter(1L, Instant.ofEpochMilli(100))
        waitingQueue.enter(2L, Instant.ofEpochMilli(200))

        val added = waitingQueue.enter(1L, Instant.ofEpochMilli(999))

        assertThat(added).isFalse()
        assertThat(waitingQueue.rank(1L)).isEqualTo(0L)
    }

    @Test
    @DisplayName("전체 대기 인원을 반환한다")
    fun countsWaiting() {
        waitingQueue.enter(1L, Instant.ofEpochMilli(100))
        waitingQueue.enter(2L, Instant.ofEpochMilli(200))

        assertThat(waitingQueue.size()).isEqualTo(2L)
    }

    @Test
    @DisplayName("대기열에 없는 유저의 순번은 null 이다")
    fun rankIsNullWhenAbsent() {
        assertThat(waitingQueue.rank(404L)).isNull()
    }

    @Test
    @DisplayName("pollNext — 앞에서부터 N명을 꺼내 대기열에서 제거한다")
    fun pollNextRemovesFront() {
        waitingQueue.enter(1L, Instant.ofEpochMilli(100))
        waitingQueue.enter(2L, Instant.ofEpochMilli(200))
        waitingQueue.enter(3L, Instant.ofEpochMilli(300))

        val polled = waitingQueue.pollNext(2)

        assertThat(polled).containsExactlyInAnyOrder(1L, 2L)
        assertThat(waitingQueue.size()).isEqualTo(1L)
        assertThat(waitingQueue.rank(3L)).isEqualTo(0L)
    }

    @Test
    @DisplayName("pollNext — 대기 인원보다 많이 요청해도 있는 만큼만 꺼낸다")
    fun pollNextClampsToSize() {
        waitingQueue.enter(1L, Instant.ofEpochMilli(100))

        val polled = waitingQueue.pollNext(10)

        assertThat(polled).containsExactly(1L)
        assertThat(waitingQueue.size()).isEqualTo(0L)
    }
}
