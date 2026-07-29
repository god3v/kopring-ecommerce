package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.kafka.EventEnvelope
import com.loopers.testcontainers.KafkaTestContainersConfig
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 지표 소비 E2E — 실 브로커·실 DB 로 "발행 → 기본 그룹 소비 → 시간별 집계 테이블(RDB SoT) 적재" 를 관통 검증한다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class, KafkaTestContainersConfig::class)
@DisplayName("지표 소비 E2E")
class MetricsConsumerIntegrationTest @Autowired constructor(
    private val objectMapper: ObjectMapper,
    private val productHourlyMetricsRepository: ProductHourlyMetricsRepository,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
) {
    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)
    private val date = LocalDate.of(2026, 7, 14)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @Test
    fun `발행된 행동 이벤트가 소비돼 시간별 집계 테이블에 쌓인다`() {
        val productId = 501L

        producer().use { it.send(ProducerRecord(CATALOG_EVENTS, productId.toString(), envelope("LIKE_CREATED", productId))).get() }

        awaitUntil("시간별 집계 적재") {
            productHourlyMetricsRepository.sumByDate(date).any { it.productId == productId && it.likeCount == 1L }
        }
    }

    private fun envelope(eventType: String, aggregateId: Long, payload: String = "{}"): ByteArray =
        objectMapper.writeValueAsBytes(
            EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                aggregateType = "PRODUCT",
                aggregateId = aggregateId.toString(),
                occurredAt = occurredAt,
                payload = objectMapper.readTree(payload),
            ),
        )

    private fun producer(): KafkaProducer<String, ByteArray> = KafkaProducer(
        mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to KafkaTestContainersConfig.bootstrapServers(),
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java.name,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to ByteArraySerializer::class.java.name,
        ),
    )

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(200)
        }
        fail("$what 이 ${AWAIT_TIMEOUT_MS}ms 안에 이루어지지 않았다")
    }

    companion object {
        private const val CATALOG_EVENTS = "catalog-events"
        private const val AWAIT_TIMEOUT_MS = 30_000L
    }
}
