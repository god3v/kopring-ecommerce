package com.loopers.interfaces.api

import com.loopers.application.payment.port.PaymentGateway
import com.loopers.application.payment.port.PgTransaction
import com.loopers.application.payment.port.PgTransactionStatus
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentRepository
import com.loopers.domain.payment.PaymentStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.payment.PaymentV1Dto
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
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentRepository,
) {
    @MockkBean
    private lateinit var paymentGateway: PaymentGateway

    private var paymentId: Long = 0L

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "스투시 반팔티", price = 1000, stock = 100))
        val order = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(productId = product.id, productName = "스투시 반팔티", unitPrice = 1000, quantity = 1)),
                idempotencyKey = "admin-sync-seed",
            ).also { it.markPaymentPending() },
        )
        // 처리 중(REQUESTED) + 거래 식별자 접수 완료 — 외부 상태 조회로 복구할 수 있는 결제.
        paymentId = paymentRepository.save(
            Payment.request(orderId = order.id, amount = 1000L).also { it.accept("tx-sync") },
        ).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api-admin/v1/payments/{paymentId}/sync 수동 복구")
    @Nested
    inner class Sync {
        @DisplayName("외부가 성공으로 확정되어 있으면, 200 과 함께 정산되어 settled=true·APPROVED 를 응답한다.")
        @Test
        fun settlesWhenExternallyConfirmed() {
            every { paymentGateway.getTransaction(any(), "tx-sync") } returns PgTransaction("tx-sync", PgTransactionStatus.SUCCESS, null)

            val response = sync(paymentId, adminHeaders())

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.settled).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.APPROVED) },
                { assertThat(paymentRepository.findById(paymentId)!!.status).isEqualTo(PaymentStatus.APPROVED) },
            )
        }

        @DisplayName("거래 식별자가 없는 처리 중 결제는, 주문 식별자로 외부 결제건을 찾아 식별자를 접수하고 정산한다(타임아웃 성공 수렴).")
        @Test
        fun recoversByOrderWhenNoTransactionId() {
            // 거래 식별자 미확보(타임아웃 접수 미확인) 결제 + 그 주문.
            val user = userRepository.save(UserFixture.validUser(loginId = "timeoutuser", email = "timeout@example.com"))
            val product = productRepository.save(ProductFixture.validProduct(name = "양말", price = 500, stock = 50))
            val order = orderRepository.save(
                Order.create(
                    userId = user.id,
                    lines = listOf(OrderLine.create(productId = product.id, productName = "양말", unitPrice = 500, quantity = 1)),
                    idempotencyKey = "no-tx-seed",
                ).also { it.markPaymentPending() },
            )
            val noKeyPaymentId = paymentRepository.save(Payment.request(orderId = order.id, amount = 500L)).id
            every { paymentGateway.getByOrder(any(), order.id) } returns
                listOf(PgTransaction("tx-recovered", PgTransactionStatus.SUCCESS, null))

            val response = sync(noKeyPaymentId, adminHeaders())

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.settled).isTrue() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.APPROVED) },
                { assertThat(paymentRepository.findById(noKeyPaymentId)!!.transactionId).isEqualTo("tx-recovered") },
            )
        }

        @DisplayName("외부가 아직 처리 중이면, 상태 변화 없이 settled=false 로 응답한다.")
        @Test
        fun leavesUnsettledWhenExternallyPending() {
            every { paymentGateway.getTransaction(any(), "tx-sync") } returns PgTransaction("tx-sync", PgTransactionStatus.PENDING, null)

            val response = sync(paymentId, adminHeaders())

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.settled).isFalse() },
                { assertThat(response.body?.data?.status).isEqualTo(PaymentStatus.REQUESTED) },
                { assertThat(paymentRepository.findById(paymentId)!!.status).isEqualTo(PaymentStatus.REQUESTED) },
            )
        }

        @DisplayName("외부 상태 조회가 실패(통신 오류·회로 차단)하면, 상태 변화 없이 settled=false 로 응답한다.")
        @Test
        fun leavesUnsettledWhenGatewayFails() {
            every { paymentGateway.getTransaction(any(), "tx-sync") } throws
                com.loopers.application.payment.port.PaymentGatewayException("회로 차단")

            val response = sync(paymentId, adminHeaders())

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.settled).isFalse() },
                { assertThat(paymentRepository.findById(paymentId)!!.status).isEqualTo(PaymentStatus.REQUESTED) },
            )
        }

        @DisplayName("존재하지 않는 결제면, 404 PAYMENT_NOT_FOUND 를 받는다.")
        @Test
        fun returnsNotFound_whenPaymentMissing() {
            val response = sync(999_999L, adminHeaders())

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PAYMENT_NOT_FOUND") },
            )
        }

        @DisplayName("LDAP 인증 헤더가 없으면, 401 UNAUTHORIZED 를 받는다.")
        @Test
        fun returnsUnauthorized_whenNoLdap() {
            val response = sync(paymentId, HttpHeaders())

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun sync(paymentId: Long, headers: HttpHeaders): ResponseEntity<ApiResponse<PaymentV1Dto.SyncResponse>> =
        testRestTemplate.exchange(
            "/api-admin/v1/payments/$paymentId/sync",
            HttpMethod.POST,
            HttpEntity<Void>(headers),
            object : ParameterizedTypeReference<ApiResponse<PaymentV1Dto.SyncResponse>>() {},
        )

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }
}
