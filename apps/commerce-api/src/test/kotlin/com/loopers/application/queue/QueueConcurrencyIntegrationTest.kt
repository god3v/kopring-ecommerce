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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 대기열 동시성 — 실제 Redis(Testcontainer)로 진입·입장 처리의 정확성을 검증한다.
 * 순서/중복 방지는 ZADD NX, 초과·중복 발급 방지는 ZPOPMIN 원자성이 담당한다(Redis 단일 스레드 실행).
 */
@SpringBootTest
@Import(RedisTestContainersConfig::class)
@DisplayName("대기열 동시성")
class QueueConcurrencyIntegrationTest @Autowired constructor(
    private val queueFacade: QueueFacade,
    private val waitingQueueRepository: WaitingQueueRepository,
    private val entryTokenStore: EntryTokenStore,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @DisplayName("[1] 서로 다른 유저가 동시에 진입하면, 유실·중복 없이 전원이 고유한 순번을 받는다.")
    @Test
    fun concurrentEnterAssignsUniqueRanks() {
        val users = 200

        runConcurrently(users) { i -> queueFacade.enter((i + 1).toLong()) }

        assertThat(waitingQueueRepository.size()).isEqualTo(users.toLong())
        val ranks = (1..users).map { waitingQueueRepository.rank(it.toLong()) }
        assertThat(ranks).doesNotContainNull()
        assertThat(ranks.toSet()).isEqualTo((0L until users.toLong()).toSet())
    }

    @DisplayName("[2] 같은 유저가 동시에 여러 번 진입해도, 한 자리만 차지한다(ZADD NX 멱등).")
    @Test
    fun concurrentReentryBySameUserIsIdempotent() {
        runConcurrently(100) { queueFacade.enter(42L) }

        assertThat(waitingQueueRepository.size()).isEqualTo(1L)
        assertThat(waitingQueueRepository.rank(42L)).isEqualTo(0L)
    }

    @DisplayName("[3] 동시 진입이 섞여도, 먼저 진입을 마친 그룹이 항상 뒤 그룹보다 앞 순번을 받는다(FIFO).")
    @Test
    fun earlierGroupAlwaysRanksAhead() {
        val groupSize = 100

        runConcurrently(groupSize) { i -> queueFacade.enter((i + 1).toLong()) }
        // score 해상도가 밀리초라, 그룹 경계가 같은 밀리초에 걸리지 않도록 최소 간격을 둔다.
        Thread.sleep(5)
        runConcurrently(groupSize) { i -> queueFacade.enter((groupSize + i + 1).toLong()) }

        val firstGroupRanks = (1..groupSize).map { waitingQueueRepository.rank(it.toLong()) }
        val secondGroupRanks = (groupSize + 1..groupSize * 2).map { waitingQueueRepository.rank(it.toLong()) }
        assertThat(firstGroupRanks).doesNotContainNull()
        assertThat(secondGroupRanks).doesNotContainNull()
        // 앞 그룹이 0..99, 뒤 그룹이 100..199 — 그룹 간 역전이 없다.
        assertThat(firstGroupRanks.filterNotNull().toSet()).isEqualTo((0L until groupSize.toLong()).toSet())
        assertThat(secondGroupRanks.filterNotNull().toSet())
            .isEqualTo((groupSize.toLong() until groupSize * 2L).toSet())
    }

    @DisplayName("[4] 배치 크기 이상이 대기 중이어도, admit 은 앞에서 배치 크기만큼만 입장시키고 나머지는 큐에 남긴다.")
    @Test
    fun admitCapsAtBatchSize() {
        (1..50).forEach { waitingQueueRepository.enter(it.toLong(), Instant.ofEpochMilli(it.toLong())) }

        val issued = queueFacade.admit(18)

        assertThat(issued).isEqualTo(18)
        assertThat(waitingQueueRepository.size()).isEqualTo(32L)
        // 앞 18명(score=1..18)만 토큰을 받고, 나머지는 아직 없다.
        assertThat((1..18).count { entryTokenStore.find(it.toLong()) != null }).isEqualTo(18)
        assertThat((19..50).count { entryTokenStore.find(it.toLong()) != null }).isEqualTo(0)
    }

    @DisplayName("[5] 여러 스케줄러가 동시에 admit 해도, 대기 인원을 넘겨 발급하거나 한 유저를 중복 발급하지 않는다(ZPOPMIN 원자성).")
    @Test
    fun concurrentAdmitNeverOverIssues() {
        val users = 100
        (1..users).forEach { waitingQueueRepository.enter(it.toLong(), Instant.ofEpochMilli(it.toLong())) }

        // 10개 스케줄러가 각각 18명씩 동시에 입장 처리 시도 → 180 시도, 실제 대기는 100.
        val totalIssued = AtomicInteger(0)
        runConcurrently(10) { totalIssued.addAndGet(queueFacade.admit(18)) }

        // 정확히 대기 인원만큼만 발급되고(초과 없음), 큐는 비며, 전원이 토큰을 하나씩 받는다(중복 없음).
        assertThat(totalIssued.get()).isEqualTo(users)
        assertThat(waitingQueueRepository.size()).isEqualTo(0L)
        assertThat((1..users).count { entryTokenStore.find(it.toLong()) != null }).isEqualTo(users)
    }

    private fun runConcurrently(threads: Int, block: (Int) -> Unit) {
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) { i ->
            executor.submit {
                try {
                    startLatch.await()
                    block(i)
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
    }
}
