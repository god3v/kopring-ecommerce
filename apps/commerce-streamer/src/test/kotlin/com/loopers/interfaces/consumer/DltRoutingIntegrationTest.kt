package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.metrics.ProductMetricsRepository
import com.loopers.kafka.EventEnvelope
import com.loopers.testcontainers.KafkaTestContainersConfig
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.kafka.support.KafkaHeaders
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID

/**
 * DLT 라우팅 E2E — 실제 브로커(Testcontainers)로 소비 실패 격리를 검증한다.
 * 형식이 깨진 메시지는 재시도 없이 `<원본토픽>-dlt` 에 원문 그대로 격리되고(ByteArraySerializer 템플릿),
 * 같은 토픽의 정상 메시지는 격리와 무관하게 집계에 반영된다 — 한 건의 poison 이 파이프라인을 막지 않는다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class, KafkaTestContainersConfig::class)
@DisplayName("DLT 라우팅 E2E")
class DltRoutingIntegrationTest @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val productMetricsRepository: ProductMetricsRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `형식이 깨진 메시지는 원문 그대로 DLT 로 격리되고, 같은 토픽의 정상 메시지는 집계에 반영된다`() {
        val malformed = "not-json".toByteArray()
        val productId = 777L
        val valid = objectMapper.writeValueAsBytes(
            EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                eventType = "LIKE_CREATED",
                aggregateType = "PRODUCT",
                aggregateId = productId.toString(),
                occurredAt = LocalDateTime.now(),
                payload = objectMapper.readTree("{}"),
            ),
        )

        producer().use { p ->
            p.send(ProducerRecord(TOPIC, productId.toString(), malformed)).get()
            p.send(ProducerRecord(TOPIC, productId.toString(), valid)).get()
        }

        // 깨진 메시지 — DLT 도착: 원문 바이트 보존 + 원본 토픽·예외 헤더
        val dltRecord = awaitDltRecord()
        assertThat(dltRecord.value()).isEqualTo(malformed)
        val headers = dltRecord.headers().associate { it.key() to String(it.value()) }
        assertThat(headers[KafkaHeaders.DLT_ORIGINAL_TOPIC]).isEqualTo(TOPIC)
        // fqcn 헤더에는 리스너 래핑 예외가 실리므로, 실제 원인은 stacktrace 헤더의 cause 체인으로 확인한다.
        assertThat(headers[KafkaHeaders.DLT_EXCEPTION_STACKTRACE]).contains("MalformedEventException")

        // 같은 키(파티션)의 정상 메시지 — 격리 뒤에도 흘러서 집계에 반영된다
        awaitUntil("좋아요 집계 반영") { productMetricsRepository.findByProductId(productId)?.likeCount == 1L }
    }

    private fun producer(): KafkaProducer<String, ByteArray> = KafkaProducer(
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainersConfig.bootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java.name,
        ),
    )

    private fun awaitDltRecord(): ConsumerRecord<String, ByteArray> {
        KafkaConsumer<String, ByteArray>(
            mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainersConfig.bootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG to "dlt-e2e-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java.name,
            ),
        ).use { consumer ->
            consumer.subscribe(listOf("$TOPIC-dlt"))
            val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val records = consumer.poll(Duration.ofMillis(500))
                if (!records.isEmpty) return records.first()
            }
        }
        fail("DLT 레코드가 ${AWAIT_TIMEOUT_MS}ms 안에 도착하지 않았다")
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        fail("$what 이 ${AWAIT_TIMEOUT_MS}ms 안에 이루어지지 않았다")
    }

    companion object {
        private const val TOPIC = "catalog-events"
        private const val AWAIT_TIMEOUT_MS = 30_000L
    }
}
