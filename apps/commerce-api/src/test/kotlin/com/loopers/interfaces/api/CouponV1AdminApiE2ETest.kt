package com.loopers.interfaces.api

import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.interfaces.api.coupon.CouponV1Dto
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CouponV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    private val issueStartAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
    private val issueEndAt: LocalDateTime = LocalDateTime.of(2026, 12, 31, 23, 59, 59)
    private val useStartAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0, 0)
    private val useEndAt: LocalDateTime = LocalDateTime.of(2027, 12, 31, 23, 59, 59)

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/coupons 목록 조회")
    @Nested
    inner class GetCoupons {
        @DisplayName("관리자 인증으로 조회하면, 200 과 최신순 페이지 봉투를 받는다.")
        @Test
        fun returnsPage_newestFirst() {
            couponRepository.save(CouponFixture.coupon(name = "A"))
            couponRepository.save(CouponFixture.coupon(name = "B"))

            val response = getCoupons()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("B", "A") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("삭제 마크된 템플릿은 목록에서 제외된다.")
        @Test
        fun excludesSoftDeleted() {
            couponRepository.save(CouponFixture.coupon(name = "살아있음"))
            val deleted = couponRepository.save(CouponFixture.coupon(name = "삭제됨"))
            deleted.softDelete(LocalDateTime.now())
            couponRepository.save(deleted)

            val response = getCoupons()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("살아있음") },
            )
        }

        @DisplayName("X-Loopers-Ldap 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun unauthorized_whenNoHeader() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons",
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

    @DisplayName("GET /api-admin/v1/coupons/{couponId} 상세 조회")
    @Nested
    inner class GetCoupon {
        @DisplayName("존재하는 템플릿을 조회하면, 200 과 상세를 받는다.")
        @Test
        fun returnsDetail() {
            val id = couponRepository.save(
                CouponFixture.coupon(name = "신규가입 10% 할인", discountType = DiscountType.RATE, discountValue = 10, minOrderAmount = 10000),
            ).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$id",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                couponResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(id) },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.RATE) },
                { assertThat(response.body?.data?.value).isEqualTo(10) },
                { assertThat(response.body?.data?.minOrderAmount).isEqualTo(10000) },
            )
        }

        @DisplayName("미존재/삭제 마크 템플릿을 조회하면, 404 COUPON_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/999999",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }
    }

    @DisplayName("POST /api-admin/v1/coupons 등록")
    @Nested
    inner class RegisterCoupon {
        @DisplayName("정상 입력으로 등록하면, 200 과 생성된 템플릿을 받는다.")
        @Test
        fun returnsCreated() {
            val response = register(
                CouponV1Dto.RegisterCouponRequest(
                    name = "신규가입 10% 할인",
                    type = "RATE",
                    value = 10,
                    minOrderAmount = 10000,
                    issueStartAt = issueStartAt,
                    issueEndAt = issueEndAt,
                    useStartAt = useStartAt,
                    useEndAt = useEndAt,
                ),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.RATE) },
            )
        }

        @DisplayName("지원하지 않는 type 으로 등록하면, 400 COUPON_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenInvalidType() {
            val response = registerAny(
                CouponV1Dto.RegisterCouponRequest(
                    name = "쿠폰",
                    type = "PERCENT",
                    value = 10,
                    minOrderAmount = null,
                    issueStartAt = issueStartAt,
                    issueEndAt = issueEndAt,
                    useStartAt = useStartAt,
                    useEndAt = useEndAt,
                ),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_BAD_REQUEST") },
            )
        }

        @DisplayName("만료 시각이 과거이면, 400 COUPON_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenPastExpiry() {
            val response = registerAny(
                CouponV1Dto.RegisterCouponRequest(
                    name = "쿠폰",
                    type = "FIXED",
                    value = 1000,
                    minOrderAmount = null,
                    issueStartAt = LocalDateTime.now().minusDays(10),
                    issueEndAt = LocalDateTime.now().minusDays(1),
                    useStartAt = LocalDateTime.now().minusDays(10),
                    useEndAt = LocalDateTime.now().plusDays(60),
                ),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_BAD_REQUEST") },
            )
        }
    }

    @DisplayName("PUT /api-admin/v1/coupons/{couponId} 수정")
    @Nested
    inner class UpdateCoupon {
        @DisplayName("정상 입력으로 수정하면, 200 과 갱신된 템플릿을 받는다.")
        @Test
        fun returnsUpdated() {
            val id = couponRepository.save(CouponFixture.coupon(name = "기존")).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$id",
                HttpMethod.PUT,
                HttpEntity(
                    CouponV1Dto.UpdateCouponRequest(
                        name = "변경",
                        type = "FIXED",
                        value = 3000,
                        minOrderAmount = 20000,
                        issueStartAt = issueStartAt,
                        issueEndAt = issueEndAt,
                        useStartAt = useStartAt,
                        useEndAt = useEndAt,
                    ),
                    adminJsonHeaders(),
                ),
                couponResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("변경") },
                { assertThat(response.body?.data?.type).isEqualTo(DiscountType.FIXED) },
                { assertThat(response.body?.data?.value).isEqualTo(3000) },
            )
        }

        @DisplayName("미존재 템플릿을 수정하면, 404 COUPON_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/999999",
                HttpMethod.PUT,
                HttpEntity(
                    CouponV1Dto.UpdateCouponRequest("x", "FIXED", 1000, null, issueStartAt, issueEndAt, useStartAt, useEndAt),
                    adminJsonHeaders(),
                ),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/coupons/{couponId} 삭제")
    @Nested
    inner class DeleteCoupon {
        @DisplayName("삭제하면 200(data null) 과 함께 템플릿이 삭제 마크되어 상세에서 404 가 된다.")
        @Test
        fun softDeletes() {
            val id = couponRepository.save(CouponFixture.coupon(name = "삭제대상")).id

            val deleteResponse = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$id",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )
            val detailResponse = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$id",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(deleteResponse.body?.data).isNull() },
                { assertThat(detailResponse.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(detailResponse.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }

        @DisplayName("미존재/이미 삭제 템플릿을 삭제하면, 404 COUPON_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/999999",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/coupons/{couponId}/issues 발급 내역")
    @Nested
    inner class GetCouponIssues {
        @DisplayName("발급 내역을 발급 최신순으로 받는다.")
        @Test
        fun returnsIssues_newestFirst() {
            val id = couponRepository.save(CouponFixture.coupon(name = "쿠폰")).id
            userCouponRepository.save(UserCoupon.issue(userId = 1L, couponId = id, usableFrom = useStartAt, expiredAt = useEndAt))
            Thread.sleep(10)
            userCouponRepository.save(UserCoupon.issue(userId = 2L, couponId = id, usableFrom = useStartAt, expiredAt = useEndAt))

            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/$id/issues",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.CouponIssuesResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.userId }).containsExactly(2L, 1L) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
            )
        }

        @DisplayName("미존재 템플릿의 발급 내역을 조회하면, 404 COUPON_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/coupons/999999/issues",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("COUPON_NOT_FOUND") },
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun getCoupons(): ResponseEntity<ApiResponse<CouponV1Dto.AdminCouponsResponse>> =
        testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.AdminCouponsResponse>>() {},
        )

    private fun register(request: CouponV1Dto.RegisterCouponRequest): ResponseEntity<ApiResponse<CouponV1Dto.AdminCouponResponse>> =
        testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            HttpEntity(request, adminJsonHeaders()),
            couponResponse(),
        )

    private fun registerAny(request: CouponV1Dto.RegisterCouponRequest): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            "/api-admin/v1/coupons",
            HttpMethod.POST,
            HttpEntity(request, adminJsonHeaders()),
            anyResponse(),
        )

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }

    private fun adminJsonHeaders(): HttpHeaders = adminHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun couponResponse() = object : ParameterizedTypeReference<ApiResponse<CouponV1Dto.AdminCouponResponse>>() {}
}
