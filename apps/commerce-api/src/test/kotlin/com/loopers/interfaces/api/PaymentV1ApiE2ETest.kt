package com.loopers.interfaces.api

import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.interfaces.api.payment.PaymentV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.utils.DatabaseCleanUp
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
    private val orderJpaRepository: OrderJpaRepository,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    private var userId: Long = 0L
    private var productId: Long = 0L

    @BeforeEach
    fun setUp() {
        signup(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_EMAIL)
        userId = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)!!.id
        productId = productRepository.save(
            ProductFixture.validProduct(name = "스투시 반팔티", price = 1000, stock = 100),
        ).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    /** 결제 가능한(CREATED) 주문을 인증 회원 소유로 만든다. */
    private fun createdOrder(ownerId: Long = userId): Long = orderRepository.save(
        Order.create(
            userId = ownerId,
            lines = listOf(OrderLine.create(productId = productId, productName = "스투시 반팔티", unitPrice = 1000, quantity = 1)),
            idempotencyKey = "order-$ownerId-${System.nanoTime()}",
        ),
    ).id

    @DisplayName("POST /api/v1/payments 결제 요청")
    @Nested
    inner class Pay {
        @DisplayName("인증 회원이 자신의 결제 가능 주문에 결제를 요청하면, 200 과 REQUESTED 접수 응답(거래 식별자·금액 포함) 을 받는다.")
        @Test
        fun acceptsPayment() {
            val orderId = createdOrder()
            every { paymentGateway.request(any()) } returns PaymentResponse("tx-key-1", PgTransactionStatus.PENDING)

            val response = pay(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(response.body?.data?.orderId).isEqualTo(orderId) },
                { assertThat(response.body?.data?.transactionKey).isEqualTo("tx-key-1") },
                { assertThat(response.body?.data?.amount).isEqualTo(1000L) },
                { assertThat(response.body?.data?.paymentId).isNotNull() },
            )
        }

        @DisplayName("결제 요청 응답 본문에는 카드 번호가 노출되지 않는다.")
        @Test
        fun neverExposesCardNumber() {
            val orderId = createdOrder()
            every { paymentGateway.request(any()) } returns PaymentResponse("tx-key-1", PgTransactionStatus.PENDING)

            val raw = testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                HttpEntity(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"), authHeaders()),
                String::class.java,
            )

            assertAll(
                { assertThat(raw.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(raw.body).doesNotContain("1234-5678-9814-1451") },
                { assertThat(raw.body).doesNotContain("cardNo") },
            )
        }

        @DisplayName("카드 번호 형식이 틀리면, 400 INVALID_CARD_NUMBER 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidCardNumber() {
            val orderId = createdOrder()

            val response = pay(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "SAMSUNG", cardNo = "1234"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("INVALID_CARD_NUMBER") },
            )
        }

        @DisplayName("지원하지 않는 카드 종류면, 400 UNSUPPORTED_CARD_TYPE 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUnsupportedCardType() {
            val orderId = createdOrder()

            val response = pay(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "LOTTE", cardNo = "1234-5678-9814-1451"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNSUPPORTED_CARD_TYPE") },
            )
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val orderId = createdOrder()
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }

            val response = testRestTemplate.exchange(
                "/api/v1/payments",
                HttpMethod.POST,
                HttpEntity(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"), headers),
                anyResponse(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }

        @DisplayName("존재하지 않는 주문이면, 404 ORDER_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenOrderMissing() {
            val response = pay(PaymentV1Dto.PayRequest(orderId = 999_999L, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_NOT_FOUND") },
            )
        }

        @DisplayName("타인 소유 주문에 결제를 요청하면, 403 ORDER_FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenOthersOrder() {
            val other = userRepository.save(UserFixture.validUser(loginId = "other", email = "other@example.com"))
            val othersOrderId = createdOrder(ownerId = other.id)

            val response = pay(PaymentV1Dto.PayRequest(orderId = othersOrderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_FORBIDDEN") },
            )
        }

        @DisplayName("결제 가능 상태(CREATED) 가 아닌 주문이면, 409 ORDER_NOT_PAYABLE 응답을 받는다.")
        @Test
        fun returnsConflict_whenOrderNotPayable() {
            val orderId = orderRepository.save(
                Order.create(
                    userId = userId,
                    lines = listOf(OrderLine.create(productId = productId, productName = "스투시 반팔티", unitPrice = 1000, quantity = 1)),
                    idempotencyKey = "order-pending-${System.nanoTime()}",
                ).also { it.markPaymentPending() },
            ).id

            val response = pay(PaymentV1Dto.PayRequest(orderId = orderId, cardType = "SAMSUNG", cardNo = "1234-5678-9814-1451"))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_NOT_PAYABLE") },
            )
        }
    }

    @DisplayName("POST /api/v1/payments/callback 결제 결과 콜백")
    @Nested
    inner class Callback {
        /** 결제대기(PAYMENT_PENDING) 주문 + 거래 식별자를 접수한 REQUESTED 결제를 만들어, 정산 대상을 준비한다. */
        private fun pendingPayment(transactionKey: String): Long {
            val orderId = orderRepository.save(
                Order.create(
                    userId = userId,
                    lines = listOf(OrderLine.create(productId = productId, productName = "스투시 반팔티", unitPrice = 1000, quantity = 1)),
                    idempotencyKey = "cb-order-${System.nanoTime()}",
                ).also { it.markPaymentPending() },
            ).id
            paymentRepository.save(Payment.request(orderId = orderId, amount = 1000L).also { it.accept(transactionKey) })
            return orderId
        }

        @DisplayName("성공 콜백을 받으면 200 을 응답하고 해당 주문이 결제완료(PAID) 가 된다.")
        @Test
        fun settlesPaidOnSuccess() {
            val orderId = pendingPayment("tx-cb-ok")

            val response = callback(PaymentV1Dto.CallbackRequest(transactionKey = "tx-cb-ok", status = PgTransactionStatus.SUCCESS))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(orderJpaRepository.findById(orderId).get().status).isEqualTo(OrderStatus.PAID) },
                { assertThat(paymentRepository.findByTransactionId("tx-cb-ok")!!.status).isEqualTo(PaymentStatus.APPROVED) },
            )
        }

        @DisplayName("실패 콜백을 받으면 200 을 응답하고 해당 주문이 결제실패(PAYMENT_FAILED) 가 되며 재고가 보상된다.")
        @Test
        fun settlesFailedWithCompensationOnFailure() {
            val orderId = pendingPayment("tx-cb-fail")
            val stockBefore = productRepository.findById(productId)!!.stock.value

            val response = callback(
                PaymentV1Dto.CallbackRequest(transactionKey = "tx-cb-fail", status = PgTransactionStatus.FAILED, reason = "LIMIT_EXCEEDED"),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(orderJpaRepository.findById(orderId).get().status).isEqualTo(OrderStatus.PAYMENT_FAILED) },
                { assertThat(paymentRepository.findByTransactionId("tx-cb-fail")!!.status).isEqualTo(PaymentStatus.FAILED) },
                { assertThat(productRepository.findById(productId)!!.stock.value).isEqualTo(stockBefore + 1) },
            )
        }

        @DisplayName("같은 콜백을 두 번 받아도 200 이며 정산은 한 번만 반영된다(주문은 PAID 유지).")
        @Test
        fun idempotentOnDuplicateCallback() {
            val orderId = pendingPayment("tx-cb-dup")
            val request = PaymentV1Dto.CallbackRequest(transactionKey = "tx-cb-dup", status = PgTransactionStatus.SUCCESS)

            val first = callback(request)
            val second = callback(request)

            assertAll(
                { assertThat(first.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(second.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(orderJpaRepository.findById(orderId).get().status).isEqualTo(OrderStatus.PAID) },
            )
        }

        @DisplayName("알 수 없는 거래 식별자 콜백도 200 으로 수신 확인되고 상태가 변하지 않는다.")
        @Test
        fun acknowledgesUnknownTransaction() {
            val orderId = pendingPayment("tx-known")

            val response = callback(PaymentV1Dto.CallbackRequest(transactionKey = "tx-unknown", status = PgTransactionStatus.SUCCESS))

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(orderJpaRepository.findById(orderId).get().status).isEqualTo(OrderStatus.PAYMENT_PENDING) },
                { assertThat(paymentRepository.findByTransactionId("tx-known")!!.status).isEqualTo(PaymentStatus.REQUESTED) },
            )
        }

        @DisplayName("콜백 본문 형식이 틀리면(필수 필드 누락), 400 BAD_REQUEST 를 받는다.")
        @Test
        fun returnsBadRequest_whenMalformedBody() {
            val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
            val response = testRestTemplate.exchange(
                "/api/v1/payments/callback",
                HttpMethod.POST,
                // transactionKey 누락 — 필수 필드 빠진 본문
                HttpEntity("""{"status":"SUCCESS"}""", headers),
                anyResponse(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun callback(request: PaymentV1Dto.CallbackRequest): ResponseEntity<ApiResponse<Any>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return testRestTemplate.exchange(
            "/api/v1/payments/callback",
            HttpMethod.POST,
            HttpEntity(request, headers),
            anyResponse(),
        )
    }

    private fun pay(request: PaymentV1Dto.PayRequest): ResponseEntity<ApiResponse<PaymentV1Dto.PayResponse>> =
        testRestTemplate.exchange(
            "/api/v1/payments",
            HttpMethod.POST,
            HttpEntity(request, authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.PayResponse>>() {},
        )

    private fun signup(loginId: String, email: String) {
        testRestTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            HttpEntity(
                UserV1Dto.SignupRequest(
                    loginId = loginId,
                    password = UserFixture.DEFAULT_PASSWORD,
                    name = UserFixture.DEFAULT_NAME,
                    birthDate = UserFixture.DEFAULT_BIRTH_DATE,
                    email = email,
                ),
            ),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun authHeaders(loginId: String = UserFixture.DEFAULT_LOGIN_ID): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set(HEADER_LOGIN_ID, loginId)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
    }

    companion object {
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
    }
}
