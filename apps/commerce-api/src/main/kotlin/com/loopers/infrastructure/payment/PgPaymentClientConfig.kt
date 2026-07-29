package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGatewayException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Duration

@Configuration
class PgPaymentClientConfig {
    /** 외부 PG 회로 차단기 — 실패·지연이 임계치를 넘으면 회로를 열어 호출을 즉시 차단한다. */
    @Bean
    fun pgCircuitBreaker(): CircuitBreaker {
        val config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50f)
            // 읽기 타임아웃(2s)보다 작게 — 느린 호출을 끊기기 전에 집계한다.
            .slowCallDurationThreshold(Duration.ofSeconds(1))
            .slowCallRateThreshold(50f)
            .waitDurationInOpenState(Duration.ofSeconds(10))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            // PG 전송 실패만 집계 — 4xx·우리 측 버그는 회로를 열지 않는다.
            .recordExceptions(PaymentGatewayException::class.java)
            .build()
        return CircuitBreaker.of("pg", config)
    }

    /** 외부 PG 재시도 — 일시 장애(PaymentGatewayException)만 지수 backoff·지터로 재시도한다. 4xx 는 제외. */
    @Bean
    fun pgRetry(): Retry {
        val config = RetryConfig.custom<Any>()
            .maxAttempts(3)
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0, 0.5))
            .retryExceptions(PaymentGatewayException::class.java)
            .ignoreExceptions(HttpClientErrorException::class.java)
            .build()
        return Retry.of("pg", config)
    }

    /** pg-simulator 연동 RestClient — 연결·읽기 타임아웃을 둬 무응답에도 스레드를 무한 점유하지 않는다. */
    @Bean
    fun pgRestClient(
        builder: RestClient.Builder,
        @Value("\${pg-simulator.base-url:http://localhost:8082}") baseUrl: String,
        @Value("\${pg-simulator.connect-timeout-ms:1000}") connectTimeoutMs: Long,
        @Value("\${pg-simulator.read-timeout-ms:2000}") readTimeoutMs: Long,
    ): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
            setReadTimeout(Duration.ofMillis(readTimeoutMs))
        }
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build()
    }
}
