package com.loopers.infrastructure.outbox

import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Outbox 가 유실 창을 어떻게 메우는지 드러낸다 — 발행이 실패해도 이벤트는 커밋된 아웃박스에 남고,
 * 브로커 복구 후 릴레이 재시도로 결국 발행된다(At Least Once).
 *
 * Outbox 가 없었다면: 유즈케이스 커밋 직후 직접 Kafka 발행이 실패하는 순간 그 이벤트는 소실된다(복구 근거 없음).
 * Outbox 가 있으면: 발행은 같은 트랜잭션에 적재된 행에 대한 별도 재시도이므로, 발행 실패는 유실이 아니라 지연일 뿐이다.
 *
 * outbox 행은 직접 심는다 — 관심사는 릴레이의 재시도·백오프·격리이지, 어느 유즈케이스가 행을 적재했는가가 아니다
 * (적재 검증은 OrderOutboxIntegrationTest).
 */
@SpringBootTest
class OutboxRelayIntegrationTest @Autowired constructor(
    private val outboxEventJpaRepository: OutboxEventJpaRepository,
    private val relay: OutboxRelay,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    // relaxUnitFun: 실제 KafkaTemplate 은 SmartInitializingSingleton 이라 기동 시 afterSingletonsInstantiated()(void) 를 호출한다.
    // void 만 relaxed 로 풀어 컨텍스트를 띄우고, send() 는 strict 로 둬 테스트가 발행을 명시 제어한다.
    @MockkBean(relaxUnitFun = true)
    private lateinit var kafkaTemplate: KafkaTemplate<Any, Any>

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `발행이 실패해도 이벤트는 outbox 에 남고, 브로커 복구 후 릴레이 재시도로 발행된다`() {
        outboxEventJpaRepository.save(outboxRow())

        // 브로커 다운 — 발행 실패
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")
        relay.relay()

        // 유실 없음: 이벤트는 여전히 PENDING 으로 outbox 에 남고, 실패 기록과 백오프가 걸린다
        val failed = outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)
        assertThat(failed).hasSize(1)
        assertThat(failed.first().retryCount).isEqualTo(1)
        assertThat(failed.first().nextRetryAt).isNotNull()
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PUBLISHED)).isEmpty()

        // 브로커 복구 — 첫 실패의 백오프(1초)가 지나야 다음 릴레이가 재시도한다
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())
        Thread.sleep(1100)
        relay.relay()

        // 재시도로 결국 발행됨
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING)).isEmpty()
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.PUBLISHED)).hasSize(1)
        verify { kafkaTemplate.send("order-events", any(), any()) }
    }

    @Test
    fun `재시도 상한을 소진한 이벤트는 FAILED 로 격리되고, 같은 애그리거트의 뒤 이벤트는 다음 주기부터 발행된다`() {
        // 9회 실패를 이미 겪은 poison 이벤트 — 백오프는 과거로 소진돼 이번 주기에 마지막 시도가 이루어진다
        val poison = outboxRow()
        repeat(9) { poison.recordFailure(LocalDateTime.now().minusHours(1), "broker down") }
        outboxEventJpaRepository.save(poison)
        val next = outboxEventJpaRepository.save(outboxRow())

        // 10번째 실패 — poison 은 FAILED 격리, 같은 애그리거트의 뒤 이벤트는 이번 주기 보류
        every { kafkaTemplate.send(any<String>(), any(), any()) } throws RuntimeException("broker down")
        relay.relay()
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.FAILED)).hasSize(1)
        assertThat(outboxEventJpaRepository.findById(next.id).get().status).isEqualTo(OutboxStatus.PENDING)

        // 다음 주기 — FAILED 는 폴링에서 빠지므로 뒤 이벤트가 발행돼 스트림이 재개된다
        every { kafkaTemplate.send(any<String>(), any(), any()) } returns CompletableFuture.completedFuture(mockk())
        relay.relay()
        assertThat(outboxEventJpaRepository.findById(next.id).get().status).isEqualTo(OutboxStatus.PUBLISHED)
        assertThat(outboxEventJpaRepository.findByStatusOrderByIdAsc(OutboxStatus.FAILED)).hasSize(1)
    }

    private fun outboxRow() = OutboxEventEntity.create(
        eventId = UUID.randomUUID(),
        aggregateType = "ORDER",
        aggregateId = "42",
        eventType = "ORDER_PAID",
        payload = "{}",
        occurredAt = LocalDateTime.now(),
    )
}
