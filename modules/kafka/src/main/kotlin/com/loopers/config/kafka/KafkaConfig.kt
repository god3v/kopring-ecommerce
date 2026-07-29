package com.loopers.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.kafka.MalformedEventException
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries
import org.springframework.kafka.support.converter.BatchMessagingMessageConverter
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter
import java.util.HashMap

@EnableKafka
@Configuration
class KafkaConfig {
    companion object {
        const val BATCH_LISTENER = "BATCH_LISTENER_DEFAULT"

        private const val MAX_POLLING_SIZE = 3000 // read 3000 msg
        private const val FETCH_MIN_BYTES = (1024 * 1024) // 1mb
        private const val FETCH_MAX_WAIT_MS = 5 * 1000 // broker waiting time = 5s
        private const val SESSION_TIMEOUT_MS = 60 * 1000 // session timeout = 1m
        private const val HEARTBEAT_INTERVAL_MS = 20 * 1000 // heartbeat interval = 20s ( 1/3 of session_timeout )
        private const val MAX_POLL_INTERVAL_MS = 2 * 60 * 1000 // max poll interval = 2m

        private const val DLT_SUFFIX = "-dlt"
        private const val DLT_MAX_RETRIES = 5
        private const val DLT_INITIAL_BACKOFF_MS = 1000L
        private const val DLT_MAX_BACKOFF_MS = 10_000L
    }

    @Bean
    fun producerFactory(
        kafkaProperties: KafkaProperties,
    ): ProducerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildProducerProperties())
        return DefaultKafkaProducerFactory(props)
    }

    @Bean
    fun consumerFactory(
        kafkaProperties: KafkaProperties,
    ): ConsumerFactory<Any, Any> {
        val props: Map<String, Any> = HashMap(kafkaProperties.buildConsumerProperties())
        return DefaultKafkaConsumerFactory(props)
    }

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<Any, Any>): KafkaTemplate<Any, Any> {
        return KafkaTemplate(producerFactory)
    }

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper): ByteArrayJsonMessageConverter {
        return ByteArrayJsonMessageConverter(objectMapper)
    }

    @Bean(BATCH_LISTENER)
    fun defaultBatchListenerContainerFactory(
        kafkaProperties: KafkaProperties,
        converter: ByteArrayJsonMessageConverter,
    ): ConcurrentKafkaListenerContainerFactory<*, *> {
        val consumerConfig = HashMap(kafkaProperties.buildConsumerProperties())
            .apply {
                put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, MAX_POLLING_SIZE)
                put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, FETCH_MIN_BYTES)
                put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, FETCH_MAX_WAIT_MS)
                put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, SESSION_TIMEOUT_MS)
                put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, HEARTBEAT_INTERVAL_MS)
                put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, MAX_POLL_INTERVAL_MS)
            }

        return ConcurrentKafkaListenerContainerFactory<Any, Any>().apply {
            consumerFactory = DefaultKafkaConsumerFactory(consumerConfig)
            containerProperties.ackMode = ContainerProperties.AckMode.MANUAL
            setBatchMessageConverter(BatchMessagingMessageConverter(converter))
            setConcurrency(3)
            isBatchListener = true
            setCommonErrorHandler(deadLetterErrorHandler(kafkaProperties))
        }
    }

    /**
     * 소비 실패를 DLT(`<원본토픽>-dlt`)로 격리하는 에러 핸들러.
     * recoverer 기본 접미사는 `.DLT` 라 미리 만들어 둔 `-dlt` 토픽(DltTopicConfig)과 어긋나므로, 목적지 리졸버로 `-dlt` 를 명시한다.
     * 원본과 같은 파티션으로 보내 DLT 파티션 배치를 원본과 맞춘다.
     * 일시 오류는 지수 백오프로 재시도 후 DLT 로, 형식이 깨진 메시지(MalformedEventException)는 재시도 없이 바로 DLT 로 보낸다.
     * 리스너가 BatchListenerFailedException 으로 실패 레코드를 지목하면 앞 레코드는 커밋되고 그 레코드만 격리된다.
     */
    private fun deadLetterErrorHandler(kafkaProperties: KafkaProperties): DefaultErrorHandler {
        // 소비 레코드 value 는 ByteArray — 기본 프로듀서(JsonSerializer)로 보내면 원문이 base64 로 감싸진다.
        // 원문 그대로 DLT 에 보존하도록 ByteArraySerializer 전용 템플릿을 쓴다.
        val producerConfig = HashMap(kafkaProperties.buildProducerProperties())
            .apply {
                put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java)
                put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java)
            }
        val dltTemplate = KafkaTemplate(DefaultKafkaProducerFactory<Any, Any>(producerConfig))
        val recoverer = DeadLetterPublishingRecoverer(dltTemplate) { record, _ ->
            TopicPartition("${record.topic()}$DLT_SUFFIX", record.partition())
        }
        val backOff = ExponentialBackOffWithMaxRetries(DLT_MAX_RETRIES).apply {
            initialInterval = DLT_INITIAL_BACKOFF_MS
            multiplier = 2.0
            maxInterval = DLT_MAX_BACKOFF_MS
        }
        return DefaultErrorHandler(recoverer, backOff).apply {
            addNotRetryableExceptions(MalformedEventException::class.java)
        }
    }
}
