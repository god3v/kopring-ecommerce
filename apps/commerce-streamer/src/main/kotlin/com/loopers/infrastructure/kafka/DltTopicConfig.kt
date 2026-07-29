package com.loopers.infrastructure.kafka

import org.apache.kafka.clients.admin.NewTopic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * 소비 실패 격리용 DLT 토픽 — DeadLetterPublishingRecoverer 는 원본과 같은 파티션의 `<원본토픽>-dlt`(기본 접미사)로 보내므로,
 * 파티션 수를 원본(3)과 맞춰 미리 만들어 둔다. 자동 생성에 맡기면 파티션 1개로 만들어져 발행이 실패할 수 있다.
 * DLT 는 소비자의 실패 보관소이므로 소비 앱(streamer)이 소유한다.
 */
@Configuration
class DltTopicConfig(
    @Value("\${loopers.kafka.topic.order-events}") private val orderEvents: String,
    @Value("\${loopers.kafka.topic.catalog-events}") private val catalogEvents: String,
    @Value("\${loopers.kafka.topic.coupon-issue-requests}") private val couponIssueRequests: String,
) {
    @Bean
    fun orderEventsDltTopic(): NewTopic = dltTopic(orderEvents)

    @Bean
    fun catalogEventsDltTopic(): NewTopic = dltTopic(catalogEvents)

    @Bean
    fun couponIssueRequestsDltTopic(): NewTopic = dltTopic(couponIssueRequests)

    private fun dltTopic(origin: String): NewTopic =
        TopicBuilder.name("$origin$DLT_SUFFIX").partitions(PARTITIONS).replicas(REPLICAS).build()

    companion object {
        private const val DLT_SUFFIX = "-dlt"
        private const val PARTITIONS = 3
        private const val REPLICAS = 1
    }
}
