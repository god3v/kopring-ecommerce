package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.coupon.CouponV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
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
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    private var userId: Long = 0L
    private var couponId: Long = 0L

    @BeforeEach
    fun setUp() {
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP,
            HttpMethod.POST,
            HttpEntity(validSignupRequest()),
            anyResponse(),
        )
        userId = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)!!.id
        couponId = couponRepository.save(
            CouponFixture.coupon(
                name = "신규가입 10% 할인",
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
            ),
        ).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/coupons/{couponId}/issue 쿠폰 발급")
    @Nested
    inner class IssueCoupon {
        @DisplayName("인증 회원이 발급하면, 200 과 AVAILABLE 발급 쿠폰을 받는다.")
        @Test
        fun returnsIssued_whenAuthenticated() {
            val response = issue(couponId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.couponId).isEqualTo(couponId) },
                { assertThat(response.body?.data?.status).isEqualTo(UserCouponStatus.AVAILABLE) },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.RATE) },
            )
        }

        @DisplayName("같은 템플릿을 다시 발급하면, 409 ALREADY_ISSUED_COUPON 응답을 받는다 (1인 1매).")
        @Test
        fun returnsConflict_whenAlreadyIssued() {
            issue(couponId)

            val response = issueAny(couponId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("ALREADY_ISSUED_COUPON") },
            )
        }

        @DisplayName("존재하지 않는 템플릿으로 발급하면, 404 COUPON_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = issueAny(999_999L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }

        @DisplayName("만료된 템플릿으로 발급하면, 400 COUPON_NOT_APPLICABLE 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenExpired() {
            val expiredId = couponRepository.save(
                CouponFixture.coupon(
                    name = "만료쿠폰",
                    issueStartAt = LocalDateTime.now().minusDays(10),
                    issueEndAt = LocalDateTime.now().minusDays(1),
                ),
            ).id

            val response = issueAny(expiredId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_APPLICABLE") },
            )
        }

        @DisplayName("인증 헤더 없이 발급하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val response = testRestTemplate.exchange(
                issueEndpoint(couponId),
                HttpMethod.POST,
                HttpEntity<Void>(HttpHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    @DisplayName("GET /api/v1/users/me/coupons 내 쿠폰 목록")
    @Nested
    inner class GetMyCoupons {
        @DisplayName("발급 후 조회하면, 200 과 보유 쿠폰(상태 AVAILABLE)을 받는다.")
        @Test
        fun returnsMyCoupons() {
            issue(couponId)

            val response = getMyCoupons()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.content?.first()?.couponId).isEqualTo(couponId) },
                { assertThat(response.body?.data?.content?.first()?.status).isEqualTo(UserCouponStatus.AVAILABLE) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("미사용 쿠폰의 사용 기간이 지나면, 노출 상태가 EXPIRED 로 파생된다.")
        @Test
        fun derivesExpiredStatus() {
            val expiredTemplateId = couponRepository.save(CouponFixture.coupon(name = "만료템플릿")).id
            // 노출 상태(EXPIRED) 는 발급 쿠폰의 expiredAt(스냅샷)으로 파생된다 — 사용 만료된 발급 쿠폰을 직접 저장한다.
            userCouponRepository.save(
                UserCoupon.issue(
                    userId = userId,
                    couponId = expiredTemplateId,
                    usableFrom = LocalDateTime.now().minusDays(30),
                    expiredAt = LocalDateTime.now().minusDays(1),
                ),
            )

            val response = getMyCoupons()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.status }).contains(UserCouponStatus.EXPIRED) },
            )
        }

        @DisplayName("보유 쿠폰이 없으면, 200 과 빈 content 를 받는다.")
        @Test
        fun returnsEmpty_whenNone() {
            val response = getMyCoupons()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("인증 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val response = testRestTemplate.exchange(
                "/api/v1/users/me/coupons",
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

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun issue(couponId: Long): ResponseEntity<ApiResponse<CouponV1Dto.IssuedCouponResponse>> =
        testRestTemplate.exchange(
            issueEndpoint(couponId),
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.IssuedCouponResponse>>() {},
        )

    private fun issueAny(couponId: Long): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            issueEndpoint(couponId),
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders()),
            anyResponse(),
        )

    private fun getMyCoupons(): ResponseEntity<ApiResponse<CouponV1Dto.MyCouponsResponse>> =
        testRestTemplate.exchange(
            "/api/v1/users/me/coupons",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.MyCouponsResponse>>() {},
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun issueEndpoint(couponId: Long): String = "/api/v1/coupons/$couponId/issue"

    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"

        private fun validSignupRequest(): UserV1Dto.SignupRequest = UserV1Dto.SignupRequest(
            loginId = UserFixture.DEFAULT_LOGIN_ID,
            password = UserFixture.DEFAULT_PASSWORD,
            name = UserFixture.DEFAULT_NAME,
            birthDate = UserFixture.DEFAULT_BIRTH_DATE,
            email = UserFixture.DEFAULT_EMAIL,
        )
    }
}
