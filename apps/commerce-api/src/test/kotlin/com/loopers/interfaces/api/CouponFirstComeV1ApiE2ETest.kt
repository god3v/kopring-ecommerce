package com.loopers.interfaces.api

import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.coupon.CouponV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
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
import org.springframework.http.ResponseEntity
import java.time.LocalDateTime

/**
 * 선착순 발급 접수(UC-10) · 결과 조회(UC-11) E2E.
 * 접수는 202 + 요청 식별자, 결과 조회는 200 + 상태. 발급 확정(ISSUED/REJECTED) 은 streamer 처리 몫이라 여기서 검증하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class CouponFirstComeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val couponRepository: CouponRepository,
) {
    @BeforeEach
    fun setUp() {
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP,
            HttpMethod.POST,
            HttpEntity(validSignupRequest()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("선착순 템플릿에 발급을 요청하면 202 와 요청 식별자를 받는다(접수됨).")
    @Test
    fun accepts202() {
        val couponId = saveFirstComeCoupon(issueLimit = 100)

        val response = requestIssue(couponId)

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED) },
            { assertThat(response.body?.data?.requestId).isNotBlank() },
            { assertThat(response.body?.data?.status).isEqualTo(IssueRequestStatus.REQUESTED) },
        )
    }

    @DisplayName("접수한 요청 식별자로 결과를 조회하면 200 과 현재 상태를 받는다.")
    @Test
    fun getResult200() {
        val couponId = saveFirstComeCoupon(issueLimit = 100)
        val requestId = requestIssue(couponId).body!!.data!!.requestId

        val response = testRestTemplate.exchange(
            "/api/v1/coupons/issue/$requestId",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.FirstComeIssueResultResponse>>() {},
        )

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.requestId).isEqualTo(requestId) },
            { assertThat(response.body?.data?.status).isEqualTo(IssueRequestStatus.REQUESTED) },
        )
    }

    @DisplayName("발급 한도가 없는 일반 템플릿에 선착순 접수를 요청하면 400 이다.")
    @Test
    fun rejectsUnlimited400() {
        val couponId = saveFirstComeCoupon(issueLimit = null)

        val response = requestIssue(couponId)

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @DisplayName("존재하지 않는 요청 식별자로 결과를 조회하면 404 이다.")
    @Test
    fun missingRequest404() {
        val response = testRestTemplate.exchange(
            "/api/v1/coupons/issue/does-not-exist",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.FirstComeIssueResultResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }

    @DisplayName("인증 없이 접수를 요청하면 401 이다.")
    @Test
    fun unauthorized401() {
        val couponId = saveFirstComeCoupon(issueLimit = 100)

        val response = testRestTemplate.exchange(
            "/api/v1/coupons/issue",
            HttpMethod.POST,
            HttpEntity(CouponV1Dto.FirstComeIssueRequest(couponId), HttpHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.FirstComeIssueResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun saveFirstComeCoupon(issueLimit: Long?): Long {
        val now = LocalDateTime.now()
        return couponRepository.save(
            Coupon.create(
                name = "선착순 쿠폰",
                discountType = DiscountType.FIXED,
                discountValue = 1000,
                minOrderAmount = null,
                issueStartAt = now.minusDays(1),
                issueEndAt = now.plusDays(30),
                useStartAt = now.minusDays(1),
                useEndAt = now.plusDays(60),
                now = now,
                issueLimit = issueLimit,
            ),
        ).id
    }

    private fun requestIssue(couponId: Long): ResponseEntity<ApiResponse<CouponV1Dto.FirstComeIssueResponse>> =
        testRestTemplate.exchange(
            "/api/v1/coupons/issue",
            HttpMethod.POST,
            HttpEntity(CouponV1Dto.FirstComeIssueRequest(couponId), authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.FirstComeIssueResponse>>() {},
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
    }

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
