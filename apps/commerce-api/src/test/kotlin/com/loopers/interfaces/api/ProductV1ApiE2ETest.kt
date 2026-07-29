package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
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
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig::class)
class ProductV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
) {
    private var brandId: Long = 0L
    private var userId: Long = 0L

    @BeforeEach
    fun setUp() {
        brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        // likedByMe 검증용 인증 회원을 signup 엔드포인트로 생성한다.
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP,
            HttpMethod.POST,
            HttpEntity(validSignupRequest()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
        userId = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)!!.id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        // 캐시 계층이 read-through 로 활성화돼 있어, TRUNCATE 후 id 재사용으로 인한 stale 캐시 오염을 막는다.
        redisCleanUp.truncateAll()
    }

    @DisplayName("GET /api/v1/products 상품 목록 조회 (인증 불필요)")
    @Nested
    inner class GetProducts {
        @DisplayName("인증 없이 조회하면, 200 과 PageResult 를 기본 정렬(latest=최신순)로 받는다.")
        @Test
        fun returnsPageEnvelope_latestByDefault() {
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "구두", brandId = brandId))

            val response = getProducts()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                // createdAt desc, id desc → 나중에 저장된 "구두" 가 먼저.
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("구두", "운동화") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
            )
        }

        @DisplayName("brandId 필터를 지정하면, 해당 브랜드 소속 상품만 응답된다.")
        @Test
        fun appliesBrandIdFilter() {
            val otherBrandId = brandRepository.save(BrandFixture.validBrand("아디다스")).id
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "삼선슬리퍼", brandId = otherBrandId))

            val response = getProducts(brandId = otherBrandId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("삼선슬리퍼") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("sort=price_asc 를 지정하면, 가격 오름차순으로 응답된다.")
        @Test
        fun sortsByPriceAsc() {
            productRepository.save(ProductFixture.validProduct(name = "high", price = 9_000L, brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "low", price = 1_000L, brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "mid", price = 5_000L, brandId = brandId))

            val response = getProducts(sort = "price_asc")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.price }).containsExactly(1_000L, 5_000L, 9_000L) },
            )
        }

        @DisplayName("허용 집합 밖 sort 를 지정하면, 400 PRODUCT_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUnsupportedSort() {
            val response = testRestTemplate.exchange(
                "/api/v1/products?sort=unknown",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_BAD_REQUEST") },
            )
        }

        @DisplayName("page / size 가 반영되고 totalElements / totalPages 가 정확하다.")
        @Test
        fun respectsPaging() {
            listOf("A", "B", "C").forEach {
                productRepository.save(ProductFixture.validProduct(name = it, brandId = brandId))
            }

            val response = getProducts(page = 0, size = 2)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(2) },
                { assertThat(response.body?.data?.size).isEqualTo(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("size 가 상한(100)을 초과하면 100 으로 캡된다.")
        @Test
        fun capsPageSizeToMax() {
            productRepository.save(ProductFixture.validProduct(name = "p", brandId = brandId))

            val response = getProducts(size = 1000)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.size).isEqualTo(100) },
            )
        }

        @DisplayName("삭제 마크된 상품은 목록에서 제외된다.")
        @Test
        fun excludesSoftDeleted() {
            productRepository.save(ProductFixture.validProduct(name = "live", brandId = brandId))
            val dead = productRepository.save(ProductFixture.validProduct(name = "dead", brandId = brandId))
            dead.softDelete()
            productRepository.save(dead)

            val response = getProducts()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("live") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("sort=likes_desc 를 지정하면, 좋아요 수 내림차순으로 응답된다.")
        @Test
        fun sortsByLikesDesc() {
            productRepository.save(ProductFixture.validProduct(name = "few", likeCount = 10L, brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "many", likeCount = 100L, brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "mid", likeCount = 30L, brandId = brandId))

            val response = getProducts(sort = "likes_desc")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.likeCount }).containsExactly(100L, 30L, 10L) },
            )
        }

        @DisplayName("두 번째 페이지(page=1)는 남은 항목을 반환한다.")
        @Test
        fun returnsRemainder_onSecondPage() {
            listOf("A", "B", "C").forEach {
                productRepository.save(ProductFixture.validProduct(name = it, brandId = brandId))
            }

            val response = getProducts(page = 1, size = 2)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                // latest(createdAt desc, id desc): [C,B] | [A] → page=1 은 가장 먼저 저장된 A.
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("A") },
                { assertThat(response.body?.data?.page).isEqualTo(1) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("정렬 키가 동률이면 타이브레이커로 페이지 간 중복/누락 없이 결정적으로 정렬된다.")
        @Test
        fun appliesIdTieBreaker_onEqualSortKey() {
            // 가격이 모두 동일 → price_asc 의 2차 정렬은 id asc (주 정렬 방향과 통일해 filesort 회피).
            val p1 = productRepository.save(ProductFixture.validProduct(name = "p1", price = 5_000L, brandId = brandId)).id
            val p2 = productRepository.save(ProductFixture.validProduct(name = "p2", price = 5_000L, brandId = brandId)).id
            val p3 = productRepository.save(ProductFixture.validProduct(name = "p3", price = 5_000L, brandId = brandId)).id

            val page0 = getProducts(sort = "price_asc", page = 0, size = 2)
            val page1 = getProducts(sort = "price_asc", page = 1, size = 2)

            assertAll(
                { assertThat(page0.body?.data?.content?.map { it.id }).containsExactly(p1, p2) },
                { assertThat(page1.body?.data?.content?.map { it.id }).containsExactly(p3) },
            )
        }

        @DisplayName("brandId 필터와 함께 허용 집합 밖 sort 를 보내도, 400 PRODUCT_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUnsupportedSortWithBrandFilter() {
            val response = testRestTemplate.exchange(
                "/api/v1/products?brandId=$brandId&sort=unknown",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_BAD_REQUEST") },
            )
        }
    }

    @DisplayName("GET /api/v1/products/{productId} 상품 상세 조회 (선택 인증)")
    @Nested
    inner class GetProductDetail {
        @DisplayName("존재하는 상품을 조회하면, 200 과 상세(브랜드명 포함)를 받는다.")
        @Test
        fun returnsDetail_whenExists() {
            val productId = productRepository.save(
                ProductFixture.validProduct(name = "운동화", price = 59_000L, brandId = brandId),
            ).id

            val response = getProductDetail(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(productId) },
                { assertThat(response.body?.data?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.price).isEqualTo(59_000L) },
                { assertThat(response.body?.data?.brandId).isEqualTo(brandId) },
                { assertThat(response.body?.data?.brandName).isEqualTo("나이키") },
            )
        }

        @DisplayName("미인증(헤더 없음) 요청은 likedByMe=false 로 응답한다.")
        @Test
        fun likedByMeFalse_whenAnonymous() {
            val productId = productRepository.save(ProductFixture.validProduct(brandId = brandId)).id

            val response = getProductDetail(productId)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.likedByMe).isFalse() },
            )
        }

        @DisplayName("좋아요한 인증 회원이 조회하면, likedByMe=true 로 응답한다.")
        @Test
        fun likedByMeTrue_whenAuthenticatedAndLiked() {
            val productId = productRepository.save(ProductFixture.validProduct(brandId = brandId)).id
            // 좋아요 등록(인증) 후 상세 조회.
            testRestTemplate.exchange(
                "/api/v1/products/$productId/likes",
                HttpMethod.POST,
                HttpEntity<Void>(authHeaders(UserFixture.DEFAULT_PASSWORD)),
                anyResponse(),
            )

            val response = getProductDetail(productId, password = UserFixture.DEFAULT_PASSWORD)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.likedByMe).isTrue() },
            )
        }

        @DisplayName("인증 헤더가 와도 인증에 실패하면, 거부 없이 likedByMe=false 로 응답한다 (선택 인증).")
        @Test
        fun likedByMeFalse_whenAuthenticationFails() {
            val productId = productRepository.save(ProductFixture.validProduct(brandId = brandId)).id

            val response = getProductDetail(productId, password = "Wrong1234!")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.likedByMe).isFalse() },
            )
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api/v1/products/999999",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }

        @DisplayName("삭제 마크된 상품을 조회하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenSoftDeleted() {
            val product = productRepository.save(ProductFixture.validProduct(brandId = brandId))
            product.softDelete()
            productRepository.save(product)

            val response = getProductDetail(product.id)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun getProducts(
        brandId: Long? = null,
        sort: String? = null,
        page: Int? = null,
        size: Int? = null,
    ): ResponseEntity<ApiResponse<ProductV1Dto.ProductsResponse>> {
        val params = listOfNotNull(
            brandId?.let { "brandId=$it" },
            sort?.let { "sort=$it" },
            page?.let { "page=$it" },
            size?.let { "size=$it" },
        )
        val url = "/api/v1/products" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return testRestTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity.EMPTY,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductsResponse>>() {},
        )
    }

    private fun getProductDetail(
        productId: Long,
        password: String? = null,
    ): ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> {
        val entity = if (password == null) HttpEntity.EMPTY else HttpEntity<Void>(authHeaders(password))
        return testRestTemplate.exchange(
            "/api/v1/products/$productId",
            HttpMethod.GET,
            entity,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {},
        )
    }

    private fun authHeaders(password: String): HttpHeaders = HttpHeaders().apply {
        set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
        set(HEADER_LOGIN_PW, password)
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}

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
