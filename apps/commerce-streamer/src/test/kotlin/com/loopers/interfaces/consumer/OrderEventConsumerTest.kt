package com.loopers.interfaces.consumer

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.listener.BatchListenerFailedException
import org.springframework.kafka.support.Acknowledgment

/**
 * 컨슈머의 실패 지목 규약 — 레코드 처리 실패를 BatchListenerFailedException 으로 감싸 어느 레코드가 실패했는지 알린다.
 * 에러 핸들러는 이 정보로 앞 레코드는 커밋하고 실패 레코드만 재시도/DLT 격리한다(배치 전체 재전달 방지).
 */
class OrderEventConsumerTest {
    private val handler = mockk<MetricsEventHandler>()
    private val acknowledgment = mockk<Acknowledgment>(relaxed = true)
    private val consumer = OrderEventConsumer(handler)

    private fun record(offset: Long, value: String) =
        ConsumerRecord("order-events", 0, offset, "key", value.toByteArray())

    @Test
    fun `전부 성공하면 배치를 ack 한다`() {
        justRun { handler.handle(any<ByteArray>()) }

        consumer.consume(listOf(record(0, "a"), record(1, "b")), acknowledgment)

        verify(exactly = 2) { handler.handle(any<ByteArray>()) }
        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `레코드 처리 실패는 실패한 레코드를 지목해 BatchListenerFailedException 으로 던지고 ack 하지 않는다`() {
        val failing = record(1, "boom")
        justRun { handler.handle(match<ByteArray> { String(it) != "boom" }) }
        every { handler.handle(match<ByteArray> { String(it) == "boom" }) } throws RuntimeException("DB down")

        val ex = assertThrows<BatchListenerFailedException> {
            consumer.consume(listOf(record(0, "ok"), failing, record(2, "after")), acknowledgment)
        }

        assertThat(ex.record.offset()).isEqualTo(1L)
        assertThat(ex.cause).hasMessage("DB down")
        verify(exactly = 0) { acknowledgment.acknowledge() }
    }
}
