package com.loopers.interfaces.api

import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.like.LikeV1Dto
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LikeV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
) {
    private var userId: Long = 0L
    private var productId: Long = 0L

    @BeforeEach
    fun setUp() {
        // 인증용 회원은 실제 signup 엔드포인트로 생성한 뒤, 숫자 식별자를 조회해 확보한다.
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP,
            HttpMethod.POST,
            HttpEntity(validSignupRequest()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        userId = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)!!.id
        productId = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 59_000L)).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("POST /api/v1/products/{productId}/likes 좋아요 등록")
    @Nested
    inner class Register {
        @DisplayName("인증 회원이 등록하면, 200 success 응답(data null)을 받는다.")
        @Test
        fun returnsSuccess_whenAuthenticated() {
            val response = registerLike(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
            )
        }

        @DisplayName("이미 좋아요한 상태에서 재요청해도, 200 success 응답을 받는다(멱등).")
        @Test
        fun isIdempotent_whenAlreadyLiked() {
            registerLike(productId)

            val response = registerLike(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("존재하지 않는 상품에 등록하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            val response = registerLike(productId = 999_999L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }

        @DisplayName("인증 헤더 없이 등록하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val response = testRestTemplate.exchange(
                likeEndpoint(productId),
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

    @DisplayName("DELETE /api/v1/products/{productId}/likes 좋아요 취소")
    @Nested
    inner class Unlike {
        @DisplayName("좋아요한 상품을 취소하면, 200 success 응답(data null)을 받는다.")
        @Test
        fun returnsSuccess_whenLiked() {
            registerLike(productId)

            val response = unlike(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
            )
        }

        @DisplayName("좋아요가 없는 상태에서 취소해도, 200 success 응답을 받는다(멱등).")
        @Test
        fun isIdempotent_whenNotLiked() {
            val response = unlike(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            )
        }

        @DisplayName("존재하지 않는 상품을 취소하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenProductMissing() {
            val response = unlike(productId = 999_999L)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }

        @DisplayName("인증 헤더 없이 취소하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val response = testRestTemplate.exchange(
                likeEndpoint(productId),
                HttpMethod.DELETE,
                HttpEntity<Void>(HttpHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    @DisplayName("GET /api/v1/users/{userId}/likes 내 좋아요 목록 조회")
    @Nested
    inner class GetMyLikes {
        @DisplayName("본인 식별자로 조회하면, 200 과 좋아요한 상품 요약(+페이지 메타)을 받는다.")
        @Test
        fun returnsPageEnvelope_whenAuthenticated() {
            registerLike(productId)

            val response = getMyLikes(userId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(1) },
                { assertThat(response.body?.data?.content?.first()?.productId).isEqualTo(productId) },
                { assertThat(response.body?.data?.content?.first()?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(1) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
            )
        }

        @DisplayName("좋아요한 상품이 없으면, 200 과 빈 content + 페이지 메타를 받는다.")
        @Test
        fun returnsEmpty_whenNoLikes() {
            val response = getMyLikes(userId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("인증된 회원과 다른 식별자로 조회하면, 403 LIKE_FORBIDDEN 응답을 받는다.")
        @Test
        fun returnsForbidden_whenOtherUser() {
            val response = getMyLikes(userId = userId + 1)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.FORBIDDEN) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("LIKE_FORBIDDEN") },
            )
        }

        @DisplayName("page / size 가 반영되고 totalElements / totalPages 가 정확하다.")
        @Test
        fun respectsPaging_andMetaIsAccurate() {
            val productId2 = productRepository.save(ProductFixture.validProduct(name = "구두")).id
            val productId3 = productRepository.save(ProductFixture.validProduct(name = "샌들")).id
            registerLike(productId)
            registerLike(productId2)
            registerLike(productId3)

            val response = getMyLikes(userId, page = 0, size = 2)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(2) },
                { assertThat(response.body?.data?.size).isEqualTo(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("인증 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun returnsUnauthorized_whenNoHeaders() {
            val response = testRestTemplate.exchange(
                myLikesEndpoint(userId),
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

    private fun registerLike(productId: Long): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            likeEndpoint(productId),
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders()),
            anyResponse(),
        )

    private fun unlike(productId: Long): ResponseEntity<ApiResponse<Any>> =
        testRestTemplate.exchange(
            likeEndpoint(productId),
            HttpMethod.DELETE,
            HttpEntity<Void>(authHeaders()),
            anyResponse(),
        )

    private fun getMyLikes(
        userId: Long,
        page: Int? = null,
        size: Int? = null,
    ): ResponseEntity<ApiResponse<LikeV1Dto.LikedProductsResponse>> =
        testRestTemplate.exchange(
            myLikesEndpoint(userId, page, size),
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders()),
            object : ParameterizedTypeReference<ApiResponse<LikeV1Dto.LikedProductsResponse>>() {},
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

    private fun likeEndpoint(productId: Long): String = "/api/v1/products/$productId/likes"

    private fun myLikesEndpoint(userId: Long, page: Int? = null, size: Int? = null): String {
        val base = "/api/v1/users/$userId/likes"
        val params = listOfNotNull(page?.let { "page=$it" }, size?.let { "size=$it" })
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
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
