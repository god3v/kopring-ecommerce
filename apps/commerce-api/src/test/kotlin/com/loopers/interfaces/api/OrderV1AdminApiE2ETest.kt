package com.loopers.interfaces.api

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.utils.DatabaseCleanUp
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val orderRepository: OrderRepository,
) {
    private var orderId: Long = 0L

    @BeforeEach
    fun setUp() {
        val user = userRepository.save(UserFixture.validUser())
        orderId = orderRepository.save(
            Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "스투시 반팔티", 1000, 2)),
                idempotencyKey = "admin-seed",
            ).also {
                it.markPaymentPending()
                it.markPaid("tx-seed", "APPROVED")
            },
        ).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/orders 주문 목록")
    @Nested
    inner class GetOrders {
        @DisplayName("관리자 인증으로 조회하면, 200 과 운영 메타(마스킹 표시명·결제 메타) 가 포함된 페이지를 받는다.")
        @Test
        fun returnsPageWithAdminMeta() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                ordersResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.content?.first()?.status).isEqualTo(OrderStatus.PAID) },
                { assertThat(response.body?.data?.content?.first()?.userMaskedName).endsWith("*") },
                { assertThat(response.body?.data?.content?.first()?.paymentTransactionId).isEqualTo("tx-seed") },
            )
        }

        @DisplayName("X-Loopers-Ldap 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoLdap() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId} 주문 상세")
    @Nested
    inner class GetOrder {
        @DisplayName("어느 회원 주문이든 관리자 인증으로 조회하면, 200 과 운영 메타가 온다.")
        @Test
        fun returnsAnyOrder() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders/$orderId",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                orderResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.PAID) },
                { assertThat(response.body?.data?.userMaskedName).endsWith("*") },
                { assertThat(response.body?.data?.paymentTransactionId).isEqualTo("tx-seed") },
            )
        }

        @DisplayName("존재하지 않는 주문이면, 404 ORDER_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/orders/999999",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_NOT_FOUND") },
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }

    private fun ordersResponse() = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.AdminOrdersResponse>>() {}

    private fun orderResponse() = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.AdminOrderResponse>>() {}

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
}
