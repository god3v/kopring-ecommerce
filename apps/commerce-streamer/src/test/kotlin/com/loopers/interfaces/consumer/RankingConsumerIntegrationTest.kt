package com.loopers.interfaces.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.config.redis.RedisConfig
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.RedisTemplate
import java.time.LocalDateTime
import java.util.UUID

/**
 * 랭킹 소비 E2E — 실 브로커(Kafka)·실 Redis 로 "발행 → 전용 그룹 소비 → 랭킹판 반영" 을 관통 검증한다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class, KafkaTestContainersConfig::class)
@DisplayName("랭킹 소비 E2E")
class RankingConsumerIntegrationTest @Autowired constructor(
    private val objectMapper: ObjectMapper,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
    private val redisCleanUp: RedisCleanUp,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val occurredAt = LocalDateTime.of(2026, 7, 14, 10, 0)
    private val key = "rank:all:20260714"

    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `발행된 행동 이벤트가 전용 그룹에서 소비돼 랭킹판 점수에 반영된다`() {
        val productId = 101L
        val message = envelope("LIKE_CREATED", productId)

        producer().use { it.send(ProducerRecord(CATALOG_EVENTS, productId.toString(), message)).get() }

        awaitUntil("좋아요 신호 랭킹 반영") { scoreOf(productId) == 0.2 }
    }

    @Test
    fun `같은 메시지를 재전달해도 랭킹판 점수는 한 번만 오른다`() {
        val productId = 202L
        // 같은 bytes = 같은 eventId. 두 번 발행해도 멱등 표식이 두 번째를 거른다.
        val message = envelope("LIKE_CREATED", productId)

        producer().use { p ->
            p.send(ProducerRecord(CATALOG_EVENTS, productId.toString(), message)).get()
            p.send(ProducerRecord(CATALOG_EVENTS, productId.toString(), message)).get()
        }

        awaitUntil("좋아요 신호 반영") { scoreOf(productId) == 0.2 }
        // 두 번째 전달이 (안) 반영될 시간을 준 뒤에도 여전히 1회분이어야 한다.
        Thread.sleep(1_000)
        assertThat(scoreOf(productId)).isEqualTo(0.2)
    }

    @Test
    fun `PRODUCT_DELETED 를 소비하면 랭킹판에서 그 상품이 사라진다`() {
        val productId = 303L
        masterTemplate.opsForZSet().add(key, productId.toString(), 5.0)
        assertThat(scoreOf(productId)).isEqualTo(5.0)

        producer().use {
            it.send(ProducerRecord(CATALOG_EVENTS, productId.toString(), envelope("PRODUCT_DELETED", productId))).get()
        }

        awaitUntil("삭제 상품 랭킹판 제거") { scoreOf(productId) == null }
    }

    @Test
    fun `주문 1건 상품이 좋아요 3건 상품보다 랭킹판에서 상위다`() {
        val ordered = 401L
        val liked = 402L
        val orderLines = """{"lines":[{"productId":$ordered,"quantity":1}]}"""

        producer().use { p ->
            // 주문 1건 → 0.7
            p.send(ProducerRecord(ORDER_EVENTS, "900", envelope("ORDER_PAID", 900L, orderLines))).get()
            // 좋아요 3건(서로 다른 eventId) → 0.2 × 3 = 0.6
            repeat(3) { p.send(ProducerRecord(CATALOG_EVENTS, liked.toString(), envelope("LIKE_CREATED", liked))).get() }
        }

        awaitUntil("가중치 반영") { scoreOf(ordered) == 0.7 && (scoreOf(liked) ?: 0.0) >= 0.59 }

        val topDown = masterTemplate.opsForZSet().reverseRange(key, 0, -1)?.toList()
        assertThat(topDown).containsExactly(ordered.toString(), liked.toString())
    }

    private fun scoreOf(productId: Long): Double? =
        masterTemplate.opsForZSet().score(key, productId.toString())

    private fun envelope(eventType: String, aggregateId: Long, payload: String = "{}"): ByteArray =
        objectMapper.writeValueAsBytes(
            EventEnvelope(
                eventId = UUID.randomUUID().toString(),
                eventType = eventType,
                aggregateType = if (eventType.startsWith("ORDER")) "ORDER" else "PRODUCT",
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
        private const val ORDER_EVENTS = "order-events"
        private const val AWAIT_TIMEOUT_MS = 30_000L
    }
}
