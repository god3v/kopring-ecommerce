package com.loopers.infrastructure.outbox

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * 발행 대상 토픽 선언 — `auto.create.topics.enable=false` 이므로 `KafkaAdmin` 이 기동 시 생성하도록 명시한다.
 * key(aggregateId) 기준 파티션 순서 보장을 위해 다중 파티션. 로컬 단일 브로커 기준 replica=1.
 */
@Configuration
class OutboxTopicConfig {
    @Bean
    fun orderEventsTopic(): NewTopic =
        TopicBuilder.name(OutboxRelay.ORDER_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build()

    @Bean
    fun catalogEventsTopic(): NewTopic =
        TopicBuilder.name(OutboxRelay.CATALOG_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build()

    @Bean
    fun couponIssueRequestsTopic(): NewTopic =
        TopicBuilder.name(OutboxRelay.COUPON_ISSUE_REQUESTS).partitions(PARTITIONS).replicas(REPLICAS).build()

    companion object {
        private const val PARTITIONS = 3
        private const val REPLICAS = 1
    }
}
