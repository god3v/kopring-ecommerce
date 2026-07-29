package com.loopers.interfaces.api

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.order.OrderV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
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
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.Duration
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class OrderV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val entryTokenStore: EntryTokenStore,
) {
    private var productId: Long = 0L
    private var userCouponId: Long = 0L

    @BeforeEach
    fun setUp() {
        signup(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_EMAIL)
        val userId = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)!!.id
        productId = productRepository.save(
            ProductFixture.validProduct(name = "스투시 반팔티", price = 1000, stock = 100),
        ).id
        val couponId = couponRepository.save(
            CouponFixture.coupon(
                name = "10% 할인",
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = null,
            ),
        ).id
        userCouponId = userCouponRepository.save(
            UserCoupon.issue(
                userId = userId,
                couponId = couponId,
                usableFrom = LocalDateTime.now().minusDays(1),
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        ).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/orders 주문 생성")
    @Nested
    inner class PlaceOrder {
        @DisplayName("쿠폰을 적용해 주문하면, 200 과 CREATED + 3금액(할인 반영) 을 받는다.")
        @Test
        fun placesWithCoupon() {
            val response = placeOrder(request(quantity = 2, userCouponId = userCouponId), idempotencyKey = "idem-1")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.status).isEqualTo(OrderStatus.CREATED) },
                { assertThat(response.body?.data?.originalAmount).isEqualTo(2000L) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(200L) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(1800L) },
                { assertThat(response.body?.data?.userCouponId).isEqualTo(userCouponId) },
            )
        }

        @DisplayName("쿠폰 없이 주문하면, 할인 0 이고 결제 금액은 상품 합계와 같다.")
        @Test
        fun placesWithoutCoupon() {
            val response = placeOrder(request(quantity = 2, userCouponId = null), idempotencyKey = "idem-no-coupon")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.discountAmount).isEqualTo(0L) },
                { assertThat(response.body?.data?.totalAmount).isEqualTo(2000L) },
                { assertThat(response.body?.data?.userCouponId).isNull() },
            )
        }

        @DisplayName("Idempotency-Key 헤더가 없으면, 400 IDEMPOTENCY_KEY_BLANK 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenNoIdempotencyKey() {
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request(quantity = 1, userCouponId = null), authHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("IDEMPOTENCY_KEY_BLANK") },
            )
        }

        @DisplayName("items 가 비어 있으면, 400 EMPTY_LINES 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenEmptyItems() {
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(OrderV1Dto.PlaceOrderRequest(items = emptyList()), authHeaders(idempotencyKey = "idem-empty")),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("EMPTY_LINES") },
            )
        }

        @DisplayName("이미 사용한 쿠폰으로 다시 주문하면, 409 ALREADY_USED_COUPON 응답을 받는다 (재사용 불가).")
        @Test
        fun returnsConflict_whenCouponAlreadyUsed() {
            placeOrder(request(quantity = 1, userCouponId = userCouponId), idempotencyKey = "idem-first")

            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request(quantity = 1, userCouponId = userCouponId), authHeaders(idempotencyKey = "idem-second")),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ALREADY_USED_COUPON") },
            )
        }

        @DisplayName("같은 멱등 키로 재요청하면, 신규 주문 없이 같은 주문 식별자로 수렴한다.")
        @Test
        fun convergesOnSameIdempotencyKey() {
            val first = placeOrder(request(quantity = 1, userCouponId = null), idempotencyKey = "idem-dup")
            val second = placeOrder(request(quantity = 1, userCouponId = null), idempotencyKey = "idem-dup")

            assertThat(second.body?.data?.orderId).isEqualTo(first.body?.data?.orderId)
        }

        @DisplayName("인증 헤더 없이 주문하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("Idempotency-Key", "idem-x")
            }
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request(quantity = 1, userCouponId = null), headers),
                anyResponse(),
            )

            assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @DisplayName("GET /api/v1/orders 내 주문 목록")
    @Nested
    inner class GetMyOrders {
        @DisplayName("주문 후 목록을 조회하면, 200 과 본인 주문(페이지 메타 포함) 을 받는다.")
        @Test
        fun returnsMyOrders() {
            placeOrder(request(quantity = 1, userCouponId = null), idempotencyKey = "idem-list")

            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders()),
                object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.MyOrdersResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("startAt 이 endAt 보다 크면, 400 INVALID_DATE_RANGE 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidDateRange() {
            val response = testRestTemplate.exchange(
                "/api/v1/orders?startAt=2026-05-10T00:00:00&endAt=2026-05-01T00:00:00",
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("INVALID_DATE_RANGE") },
            )
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId} 내 주문 상세")
    @Nested
    inner class GetMyOrderDetail {
        @DisplayName("존재하지 않는 주문이면, 404 ORDER_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api/v1/orders/999999",
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_NOT_FOUND") },
            )
        }

        @DisplayName("타인의 주문을 조회하면, 403 ORDER_FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenOthersOrder() {
            // 다른 회원이 만든 주문을 만든다.
            signup("other", "other@example.com")
            val othersOrderId = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(
                    request(quantity = 1, userCouponId = null),
                    headers(loginId = "other", idempotencyKey = "idem-other"),
                ),
                orderResponse(),
            ).body!!.data!!.orderId

            val response = testRestTemplate.exchange(
                "/api/v1/orders/$othersOrderId",
                HttpMethod.GET,
                HttpEntity<Void>(authHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ORDER_FORBIDDEN") },
            )
        }
    }

    @DisplayName("입장 토큰 관문")
    @Nested
    inner class EntryTokenGate {
        @DisplayName("입장 토큰이 없으면, 403 ENTRY_TOKEN_REQUIRED 응답을 받는다.")
        @Test
        fun returnsForbidden_whenNoToken() {
            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(
                    request(quantity = 1, userCouponId = null),
                    headers(UserFixture.DEFAULT_LOGIN_ID, idempotencyKey = "idem-gate-1", withEntryToken = false),
                ),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ENTRY_TOKEN_REQUIRED") },
            )
        }

        @DisplayName("입장 토큰이 유효하지 않으면, 403 ENTRY_TOKEN_INVALID 응답을 받는다.")
        @Test
        fun returnsForbidden_whenInvalidToken() {
            val bogusHeaders = headers(UserFixture.DEFAULT_LOGIN_ID, idempotencyKey = "idem-gate-2", withEntryToken = false)
                .apply { set(HEADER_ENTRY_TOKEN, "bogus-token") }

            val response = testRestTemplate.exchange(
                "/api/v1/orders",
                HttpMethod.POST,
                HttpEntity(request(quantity = 1, userCouponId = null), bogusHeaders),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ENTRY_TOKEN_INVALID") },
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun request(quantity: Int, userCouponId: Long?): OrderV1Dto.PlaceOrderRequest =
        OrderV1Dto.PlaceOrderRequest(
            items = listOf(OrderV1Dto.OrderLineRequest(productId = productId, quantity = quantity)),
            userCouponId = userCouponId,
        )

    private fun placeOrder(
        request: OrderV1Dto.PlaceOrderRequest,
        idempotencyKey: String,
    ): ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> = testRestTemplate.exchange(
        "/api/v1/orders",
        HttpMethod.POST,
        HttpEntity(request, authHeaders(idempotencyKey = idempotencyKey)),
        orderResponse(),
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
            anyResponse(),
        )
    }

    private fun authHeaders(
        loginId: String = UserFixture.DEFAULT_LOGIN_ID,
        idempotencyKey: String? = null,
    ): HttpHeaders = headers(loginId, idempotencyKey)

    // 주문 API 는 입장 토큰 관문 뒤에 있다. 정상 주문 경로는 대기열에서 입장한 상태를 가정하므로,
    // 실제 발급 흐름(스케줄러) 대신 토큰을 직접 seed 해 X-Entry-Token 을 함께 보낸다.
    private fun headers(
        loginId: String,
        idempotencyKey: String? = null,
        withEntryToken: Boolean = true,
    ): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        set(HEADER_LOGIN_ID, loginId)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
        if (idempotencyKey != null) set("Idempotency-Key", idempotencyKey)
        if (withEntryToken) {
            userRepository.findByLoginId(loginId)?.let { user ->
                val token = EntryToken.issue()
                entryTokenStore.issue(user.id, token, Duration.ofMinutes(5))
                set(HEADER_ENTRY_TOKEN, token.value)
            }
        }
    }

    private fun orderResponse() = object : ParameterizedTypeReference<ApiResponse<OrderV1Dto.OrderResponse>>() {}

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    companion object {
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        private const val HEADER_ENTRY_TOKEN = "X-Entry-Token"
    }
}
