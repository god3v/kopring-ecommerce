package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.product.ProductV1Dto
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
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
) {
    private var brandId: Long = 0L

    @BeforeEach
    fun setUp() {
        brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/products (관리자) 상품 목록 조회")
    @Nested
    inner class GetProducts {
        @DisplayName("관리자 인증으로 조회하면, 200 과 PageResult 를 최신순으로 받고 항목에 salesStatus 가 포함된다.")
        @Test
        fun returnsPageEnvelope_newestFirst_withSalesStatus() {
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "구두", brandId = brandId))

            val response = getProducts()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("구두", "운동화") },
                { assertThat(response.body?.data?.content?.map { it.salesStatus }).containsOnly("on_sale") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
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
            )
        }

        @DisplayName("등록된 상품이 없으면, 200 과 빈 content + 페이지 메타를 받는다.")
        @Test
        fun returnsEmpty_whenNone() {
            val response = getProducts()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
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

        @DisplayName("브랜드 필터 없이 조회하면, 여러 브랜드 상품이 모두 집계된다 (findAll 분기).")
        @Test
        fun countsAcrossBrands_whenNoBrandFilter() {
            val otherBrandId = brandRepository.save(BrandFixture.validBrand("아디다스")).id
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "구두", brandId = brandId))
            productRepository.save(ProductFixture.validProduct(name = "삼선슬리퍼", brandId = otherBrandId))

            val response = getProducts()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(3) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
            )
        }

        @DisplayName("범위를 벗어난 페이지(page=1, 데이터 없음)를 조회해도, 200 과 빈 content + 메타를 받는다.")
        @Test
        fun returnsEmpty_whenPageOutOfBounds() {
            val response = getProducts(page = 1, size = 20)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(0) },
            )
        }

        @DisplayName("X-Loopers-Ldap 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun unauthorized_whenNoAdminHeader() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/products",
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

    @DisplayName("GET /api-admin/v1/products/{productId} (관리자) 상품 상세 조회")
    @Nested
    inner class GetProductDetail {
        @DisplayName("존재하는 상품을 조회하면, 200 과 상세(salesStatus 포함)를 받는다.")
        @Test
        fun returnsDetail_whenExists() {
            val productId = productRepository.save(
                ProductFixture.validProduct(name = "운동화", price = 59_000L, brandId = brandId),
            ).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/$productId",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.AdminProductDetailResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(productId) },
                { assertThat(response.body?.data?.brandName).isEqualTo("나이키") },
                { assertThat(response.body?.data?.salesStatus).isEqualTo("on_sale") },
            )
        }

        @DisplayName("존재하지 않거나 삭제 마크된 상품을 조회하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/999999",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }
    }

    @DisplayName("POST /api-admin/v1/products (관리자) 상품 등록")
    @Nested
    inner class RegisterProduct {
        @DisplayName("정상 입력으로 등록하면, 200 과 생성 상품(salesStatus=on_sale, likeCount=0)을 받는다.")
        @Test
        fun returnsCreated_whenValid() {
            val response = registerProduct(
                ProductV1Dto.RegisterProductRequest(brandId = brandId, name = "운동화", price = 59_000L, stock = 100),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.name).isEqualTo("운동화") },
                { assertThat(response.body?.data?.salesStatus).isEqualTo("on_sale") },
                { assertThat(response.body?.data?.likeCount).isEqualTo(0L) },
            )
        }

        @DisplayName("이름이 비어 있으면, 400 PRODUCT_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenBlankName() {
            val response = registerProduct(
                ProductV1Dto.RegisterProductRequest(brandId = brandId, name = "   ", price = 59_000L, stock = 100),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_BAD_REQUEST") },
            )
        }

        @DisplayName("지정 브랜드가 존재하지 않으면, 404 BRAND_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenBrandMissing() {
            val response = registerProduct(
                ProductV1Dto.RegisterProductRequest(brandId = 999999L, name = "운동화", price = 59_000L, stock = 100),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }

        @DisplayName("같은 브랜드에 같은 이름의 상품이 이미 있으면, 409 DUPLICATE_PRODUCT_NAME 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicateName() {
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))

            val response = registerProduct(
                ProductV1Dto.RegisterProductRequest(brandId = brandId, name = "운동화", price = 59_000L, stock = 100),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_PRODUCT_NAME") },
            )
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId} (관리자) 상품 정보 수정")
    @Nested
    inner class UpdateProduct {
        @DisplayName("정상 입력으로 수정하면, 200 과 갱신된 상품(name/price/salesStatus)을 받는다.")
        @Test
        fun returnsUpdated_whenValid() {
            val productId = productRepository.save(
                ProductFixture.validProduct(name = "운동화", price = 59_000L, brandId = brandId),
            ).id

            val response = updateProduct(
                productId,
                ProductV1Dto.UpdateProductRequest(name = "운동화 v2", price = 64_000L, salesStatus = "off_sale"),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.name).isEqualTo("운동화 v2") },
                { assertThat(response.body?.data?.price).isEqualTo(64_000L) },
                { assertThat(response.body?.data?.salesStatus).isEqualTo("off_sale") },
            )
        }

        @DisplayName("존재하지 않거나 삭제 마크된 상품을 수정하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = updateProduct(
                999999L,
                ProductV1Dto.UpdateProductRequest(name = "x", price = 1L, salesStatus = "on_sale"),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }

        @DisplayName("같은 브랜드에 같은 이름의 다른 상품이 있으면, 409 DUPLICATE_PRODUCT_NAME 응답을 받는다 (자기 자신 제외).")
        @Test
        fun returnsConflict_whenDuplicateName() {
            productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId))
            val targetId = productRepository.save(ProductFixture.validProduct(name = "구두", brandId = brandId)).id

            val response = updateProduct(
                targetId,
                ProductV1Dto.UpdateProductRequest(name = "운동화", price = 1_000L, salesStatus = "on_sale"),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_PRODUCT_NAME") },
            )
        }

        @DisplayName("지원하지 않는 salesStatus 로 수정하면, 400 PRODUCT_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenUnsupportedSalesStatus() {
            val productId = productRepository.save(ProductFixture.validProduct(brandId = brandId)).id

            val response = updateProduct(
                productId,
                ProductV1Dto.UpdateProductRequest(name = "운동화 v2", price = 64_000L, salesStatus = "unknown"),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_BAD_REQUEST") },
            )
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId} (관리자) 상품 삭제")
    @Nested
    inner class DeleteProduct {
        @DisplayName("삭제하면 200(data null) 과 함께 상품이 삭제 마크되어 회원 상세에서도 404 가 된다.")
        @Test
        fun softDeletesProduct() {
            val productId = productRepository.save(ProductFixture.validProduct(name = "운동화", brandId = brandId)).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/$productId",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            // 삭제 후 회원 상세 조회는 404.
            val memberDetail = testRestTemplate.exchange(
                "/api/v1/products/$productId",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
                { assertThat(productRepository.findById(productId)).isNull() },
                { assertThat(memberDetail.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(memberDetail.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }

        @DisplayName("존재하지 않거나 이미 삭제 마크된 상품을 삭제하면, 404 PRODUCT_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/products/999999",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                anyResponse(),
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("PRODUCT_NOT_FOUND") },
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun getProducts(
        brandId: Long? = null,
        page: Int? = null,
        size: Int? = null,
    ): ResponseEntity<ApiResponse<ProductV1Dto.AdminProductsResponse>> {
        val params = listOfNotNull(
            brandId?.let { "brandId=$it" },
            page?.let { "page=$it" },
            size?.let { "size=$it" },
        )
        val url = "/api-admin/v1/products" + if (params.isEmpty()) "" else "?${params.joinToString("&")}"
        return testRestTemplate.exchange(
            url,
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.AdminProductsResponse>>() {},
        )
    }

    private fun registerProduct(
        request: ProductV1Dto.RegisterProductRequest,
    ): ResponseEntity<ApiResponse<ProductV1Dto.AdminProductDetailResponse>> =
        testRestTemplate.exchange(
            "/api-admin/v1/products",
            HttpMethod.POST,
            HttpEntity(request, adminJsonHeaders()),
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.AdminProductDetailResponse>>() {},
        )

    private fun updateProduct(
        productId: Long,
        request: ProductV1Dto.UpdateProductRequest,
    ): ResponseEntity<ApiResponse<ProductV1Dto.AdminProductDetailResponse>> =
        testRestTemplate.exchange(
            "/api-admin/v1/products/$productId",
            HttpMethod.PUT,
            HttpEntity(request, adminJsonHeaders()),
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.AdminProductDetailResponse>>() {},
        )

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }

    private fun adminJsonHeaders(): HttpHeaders = adminHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    private fun anyResponse() = object : ParameterizedTypeReference<ApiResponse<Any>>() {}
}
