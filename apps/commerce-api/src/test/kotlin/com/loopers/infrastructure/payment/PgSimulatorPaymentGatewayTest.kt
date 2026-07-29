package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentGatewayException
import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PgTransactionStatus
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.time.Duration
import kotlin.system.measureTimeMillis

class PgSimulatorPaymentGatewayTest {
    private fun cb(config: CircuitBreakerConfig = CircuitBreakerConfig.ofDefaults()): CircuitBreaker =
        CircuitBreaker.of("test", config)

    // 기본 maxAttempts=1 → 재시도 없음(기존 테스트 행위 보존). 재시도 케이스만 명시적으로 횟수·대기를 올린다.
    private fun retry(maxAttempts: Int = 1, waitMillis: Long = 1): Retry =
        Retry.of(
            "test",
            RetryConfig.custom<Any>()
                .maxAttempts(maxAttempts)
                .waitDuration(Duration.ofMillis(waitMillis))
                .retryExceptions(PaymentGatewayException::class.java)
                .build(),
        )

    private fun request() = PaymentRequestCommand(
        userId = "7",
        orderId = 1000001L,
        amount = 31500L,
        cardType = "SAMSUNG",
        cardNo = "1234-5678-9814-1451",
        callbackUrl = "http://localhost:8080/api/v1/payments/callback",
    )

    @DisplayName("request 는 PG 에 결제를 요청하고 발급된 거래 식별자와 처리 중(PENDING) 상태를 받는다.")
    @Test
    fun requestReturnsTransactionKeyAndPending() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry())

        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-USER-ID", "7"))
            .andExpect(jsonPath("$.orderId").value("1000001"))
            .andExpect(jsonPath("$.cardType").value("SAMSUNG"))
            .andExpect(jsonPath("$.amount").value(31500))
            .andExpect(jsonPath("$.callbackUrl").value("http://localhost:8080/api/v1/payments/callback"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"transactionKey":"20260623:TR:9577c5","status":"PENDING","reason":null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = gateway.request(request())

        assertThat(result.transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(result.status).isEqualTo(PgTransactionStatus.PENDING)
        server.verify()
    }

    @DisplayName("getTransaction 은 거래 식별자로 외부 결제 상태(성공/실패/처리중)를 조회한다.")
    @Test
    fun getTransactionReturnsStatus() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry())

        // RestClient 가 path 변수의 콜론을 %3A 로 인코딩한다(서버는 디코딩해 매칭). 실제 송신 URI 를 검증한다.
        server.expect(requestTo("http://pg.test/api/v1/payments/20260623%3ATR%3A9577c5"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "7"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"transactionKey":"20260623:TR:9577c5","orderId":"1000001","cardType":"SAMSUNG",""" +
                        """"cardNo":"1234-5678-9814-1451","amount":31500,"status":"SUCCESS","reason":null}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val tx = gateway.getTransaction(userId = "7", transactionKey = "20260623:TR:9577c5")

        assertThat(tx.transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(tx.status).isEqualTo(PgTransactionStatus.SUCCESS)
        server.verify()
    }

    @DisplayName("getByOrder 는 주문 식별자로 외부 결제건들을 조회한다 (거래 식별자 미확보 복구).")
    @Test
    fun getByOrderReturnsTransactions() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry())

        server.expect(requestTo("http://pg.test/api/v1/payments?orderId=1000001"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-USER-ID", "7"))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},""" +
                        """"data":{"orderId":"1000001","transactions":[""" +
                        """{"transactionKey":"20260623:TR:9577c5","status":"SUCCESS","reason":null}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val txs = gateway.getByOrder(userId = "7", orderId = 1000001L)

        assertThat(txs).hasSize(1)
        assertThat(txs.single().transactionKey).isEqualTo("20260623:TR:9577c5")
        assertThat(txs.single().status).isEqualTo(PgTransactionStatus.SUCCESS)
        server.verify()
    }

    @DisplayName("외부가 5xx 로 실패하면 RestClient 예외가 누수되지 않고 PaymentGatewayException 으로 변환된다.")
    @Test
    fun translatesServerErrorToGatewayException() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry())

        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withServerError())

        assertThatThrownBy { gateway.request(request()) }
            .isInstanceOf(PaymentGatewayException::class.java)
        server.verify()
    }

    @DisplayName("외부가 4xx(클라이언트 오류)면 감싸지 않고 HttpClientErrorException 그대로 전파하며 재시도하지 않는다 — 일시 장애와 구분된다.")
    @Test
    fun doesNotRetryClientError() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry(maxAttempts = 3))

        // 4xx 는 한 번만 응답 — 재시도 제외(ignoreExceptions/화이트리스트 밖)라 1번만 호출돼야 한다.
        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertThatThrownBy { gateway.request(request()) }
            .isInstanceOf(HttpClientErrorException::class.java)
        server.verify() // 정확히 1번 호출(재시도 없음)
    }

    @DisplayName("외부가 반복 실패하면 회로가 열려, 이후 호출은 외부를 거치지 않고 PaymentGatewayException 으로 차단된다.")
    @Test
    fun opensCircuitAfterRepeatedFailures() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val circuit = cb(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build(),
        )
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), circuit, retry())

        // 외부는 딱 2번만 실패 응답한다 — 회로가 열리면 3번째 호출은 외부로 가지 않는다.
        repeat(2) {
            server.expect(requestTo("http://pg.test/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError())
        }

        repeat(2) {
            assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)
        }
        // 회로 OPEN — 외부를 거치지 않고 즉시 차단된다.
        assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)

        assertThat(circuit.state).isEqualTo(CircuitBreaker.State.OPEN)
        server.verify() // 외부 호출은 정확히 2번만 일어났다(3번째는 차단)
    }

    @DisplayName("일시적 통신 실패(5xx)는 설정한 최대 시도 횟수까지 재시도된다.")
    @Test
    fun retriesTransientFailureUpToMaxAttempts() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry(maxAttempts = 3))

        // 외부가 3번 모두 5xx 로 실패한다 — 최대 시도(3회) 만큼 호출되어야 한다.
        repeat(3) {
            server.expect(requestTo("http://pg.test/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError())
        }

        assertThatThrownBy { gateway.request(request()) }
            .isInstanceOf(PaymentGatewayException::class.java)
        server.verify() // 정확히 3번 시도됐다
    }

    @DisplayName("재시도 대상이 아닌 예외(응답 본문 누락 등)는 재시도 없이 즉시 전파된다.")
    @Test
    fun doesNotRetryNonRetryableException() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), cb(), retry(maxAttempts = 3))

        // 200 이지만 data 가 비어 있다 → requireNotNull 실패(IllegalArgumentException). 재시도 화이트리스트 밖이라 1번만 호출된다.
        server.expect(requestTo("http://pg.test/api/v1/payments"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    """{"meta":{"result":"SUCCESS","errorCode":null,"message":null},"data":null}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThatThrownBy { gateway.request(request()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        server.verify() // 정확히 1번만 호출(재시도 없음)
    }

    @DisplayName("재시도 사이에 backoff 대기를 둔다 — 시도 간 대기 시간만큼 지연된다.")
    @Test
    fun waitsBetweenRetries() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val gateway = PgSimulatorPaymentGateway(
            RestClientPaymentApiClient(builder.build()),
            cb(),
            retry(maxAttempts = 3, waitMillis = 50),
        )

        // 3회 모두 5xx → 시도 사이에 2번의 backoff(각 50ms) 가 끼어든다.
        repeat(3) {
            server.expect(requestTo("http://pg.test/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError())
        }

        val elapsed = measureTimeMillis {
            assertThatThrownBy { gateway.request(request()) }
                .isInstanceOf(PaymentGatewayException::class.java)
        }

        // 2번의 대기(2 × 50ms) 만큼은 최소로 지연된다. 하한만 검증(상한은 부하에 흔들리므로 보지 않는다).
        assertThat(elapsed).isGreaterThanOrEqualTo(90L)
        server.verify()
    }

    @DisplayName("한 번의 request() 안 재시도는 회로 차단기에 한 번의 실패로만 집계된다 — 재시도가 permit 을 개별 소비하지 않는다.")
    @Test
    fun retriesCountAsSingleCircuitEvent() {
        val builder = RestClient.builder().baseUrl("http://pg.test")
        val server = MockRestServiceServer.bindTo(builder).build()
        val circuit = cb(
            CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build(),
        )
        val gateway = PgSimulatorPaymentGateway(RestClientPaymentApiClient(builder.build()), circuit, retry(maxAttempts = 3))

        // 모든 시도에 5xx — 6번(요청 2 × 재시도 3) 기대를 미리 등록한다(MockServer 는 호출 시작 후 기대 추가 불가).
        repeat(6) {
            server.expect(requestTo("http://pg.test/api/v1/payments"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError())
        }

        // 첫 request(): 3번 재시도해도 회로 차단기에는 실패 1건만 집계된다 → 윈도우(2) 미달, 회로는 닫힌 채.
        assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)
        assertThat(circuit.state).isEqualTo(CircuitBreaker.State.CLOSED)

        // 둘째 request(): 실패 2건째 → 윈도우(2) 충족, 실패율 100% → 회로 OPEN.
        assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)
        assertThat(circuit.state).isEqualTo(CircuitBreaker.State.OPEN)

        // 셋째 request(): OPEN — 재시도에 들어가기 전에 차단되어 외부로 한 번도 나가지 않는다.
        assertThatThrownBy { gateway.request(request()) }.isInstanceOf(PaymentGatewayException::class.java)

        server.verify() // 외부 호출은 정확히 6번(요청 2 × 재시도 3) — 셋째 요청은 0번
    }
}
