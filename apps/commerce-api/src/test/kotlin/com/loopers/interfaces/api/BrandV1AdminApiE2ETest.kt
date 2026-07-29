package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.interfaces.api.brand.BrandV1Dto
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1AdminApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api-admin/v1/brands (관리자) 브랜드 목록 조회")
    @Nested
    inner class GetBrands {
        @DisplayName("관리자 인증으로 조회하면, 200 과 페이지 봉투를 최신순으로 받는다.")
        @Test
        fun returnsPageEnvelope_newestFirst() {
            brandRepository.save(BrandFixture.validBrand("A"))
            brandRepository.save(BrandFixture.validBrand("B"))

            val response = getBrands()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                // createdAt desc, id desc → 나중에 저장된 B 가 먼저.
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("B", "A") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(2L) },
                { assertThat(response.body?.data?.page).isEqualTo(0) },
            )
        }

        @DisplayName("등록된 브랜드가 없으면, 200 과 빈 content + 페이지 메타를 받는다.")
        @Test
        fun returnsEmpty_whenNone() {
            val response = getBrands()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).isEmpty() },
                { assertThat(response.body?.data?.totalElements).isEqualTo(0L) },
            )
        }

        @DisplayName("삭제 마크된 브랜드는 목록에서 제외된다.")
        @Test
        fun excludesSoftDeleted() {
            brandRepository.save(BrandFixture.validBrand("나이키"))
            val deleted = brandRepository.save(BrandFixture.validBrand("아디다스"))
            deleted.softDelete()
            brandRepository.save(deleted)

            val response = getBrands()

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content?.map { it.name }).containsExactly("나이키") },
                { assertThat(response.body?.data?.totalElements).isEqualTo(1L) },
            )
        }

        @DisplayName("page / size 가 반영되고 totalElements / totalPages 가 정확하다.")
        @Test
        fun respectsPaging() {
            listOf("A", "B", "C").forEach { brandRepository.save(BrandFixture.validBrand(it)) }

            val response = getBrands(page = 0, size = 2)

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.content).hasSize(2) },
                { assertThat(response.body?.data?.size).isEqualTo(2) },
                { assertThat(response.body?.data?.totalElements).isEqualTo(3L) },
                { assertThat(response.body?.data?.totalPages).isEqualTo(2) },
            )
        }

        @DisplayName("X-Loopers-Ldap 헤더 없이 조회하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        fun unauthorized_whenNoAdminHeader() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.GET,
                HttpEntity<Void>(HttpHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("UNAUTHORIZED") },
            )
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId} (관리자) 브랜드 상세 조회")
    @Nested
    inner class GetBrandDetail {
        @DisplayName("존재하는 브랜드를 조회하면, 200 과 { id, name } 을 받는다.")
        @Test
        fun returnsBrand_whenExists() {
            val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/$brandId",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.AdminBrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(brandId) },
                { assertThat(response.body?.data?.name).isEqualTo("나이키") },
            )
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 BRAND_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/999999",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }

        @DisplayName("삭제 마크된 브랜드를 조회하면, 404 BRAND_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenSoftDeleted() {
            val brand = brandRepository.save(BrandFixture.validBrand("나이키"))
            brand.softDelete()
            brandRepository.save(brand)

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/${brand.id}",
                HttpMethod.GET,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }
    }

    @DisplayName("POST /api-admin/v1/brands (관리자) 브랜드 등록")
    @Nested
    inner class RegisterBrand {
        @DisplayName("정상 입력으로 등록하면, 200 과 생성된 브랜드를 받는다.")
        @Test
        fun returnsCreated_whenValid() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.POST,
                HttpEntity(BrandV1Dto.RegisterBrandRequest(name = "나이키"), adminJsonHeaders()),
                object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.AdminBrandResponse>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isNotNull() },
                { assertThat(response.body?.data?.name).isEqualTo("나이키") },
            )
        }

        @DisplayName("이름이 비어 있으면, 400 BRAND_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenBlankName() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.POST,
                HttpEntity(BrandV1Dto.RegisterBrandRequest(name = "   "), adminJsonHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_BAD_REQUEST") },
            )
        }

        @DisplayName("이미 존재하는 이름으로 등록하면, 409 DUPLICATE_BRAND_NAME 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicate() {
            brandRepository.save(BrandFixture.validBrand("나이키"))

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.POST,
                HttpEntity(BrandV1Dto.RegisterBrandRequest(name = "나이키"), adminJsonHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_BRAND_NAME") },
            )
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId} (관리자) 브랜드 정보 수정")
    @Nested
    inner class UpdateBrand {
        @DisplayName("정상 입력으로 수정하면, 200 과 갱신된 브랜드를 받는다.")
        @Test
        fun returnsUpdated_whenValid() {
            val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id

            val response = updateBrand(brandId, "나이키 코리아")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.data?.id).isEqualTo(brandId) },
                { assertThat(response.body?.data?.name).isEqualTo("나이키 코리아") },
            )
        }

        @DisplayName("존재하지 않거나 삭제 마크된 브랜드를 수정하면, 404 BRAND_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = updateBrand(999999L, "아무거나")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }

        @DisplayName("다른 브랜드와 이름이 중복되면, 409 DUPLICATE_BRAND_NAME 응답을 받는다.")
        @Test
        fun returnsConflict_whenDuplicate() {
            brandRepository.save(BrandFixture.validBrand("나이키"))
            val targetId = brandRepository.save(BrandFixture.validBrand("아디다스")).id

            val response = updateBrand(targetId, "나이키")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("DUPLICATE_BRAND_NAME") },
            )
        }

        @DisplayName("이름이 비어 있으면, 400 BRAND_BAD_REQUEST 응답을 받는다.")
        @Test
        fun returnsBadRequest_whenBlankName() {
            val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id

            val response = updateBrand(brandId, "   ")

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_BAD_REQUEST") },
            )
        }

        private fun updateBrand(brandId: Long, name: String) =
            testRestTemplate.exchange(
                "/api-admin/v1/brands/$brandId",
                HttpMethod.PUT,
                HttpEntity(BrandV1Dto.UpdateBrandRequest(name = name), adminJsonHeaders()),
                object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.AdminBrandResponse>>() {},
            )
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId} (관리자) 브랜드 삭제 — 카스케이드")
    @Nested
    inner class DeleteBrand {
        @DisplayName("삭제하면 200(data null) 과 함께 브랜드·소속 상품이 모두 삭제 마크된다.")
        @Test
        fun deletesBrandAndCascadesProducts() {
            val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
            val productId = productRepository.save(ProductFixture.validProduct(brandId = brandId, name = "운동화")).id

            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/$brandId",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
                { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
                { assertThat(response.body?.data).isNull() },
                // 카스케이드: 브랜드와 소속 상품 모두 삭제 마크 → @SQLRestriction 으로 조회 시 null.
                { assertThat(brandRepository.findById(brandId)).isNull() },
                { assertThat(productRepository.findById(productId)).isNull() },
            )
        }

        @DisplayName("존재하지 않거나 이미 삭제 마크된 브랜드를 삭제하면, 404 BRAND_NOT_FOUND 응답을 받는다.")
        @Test
        fun returnsNotFound_whenMissing() {
            val response = testRestTemplate.exchange(
                "/api-admin/v1/brands/999999",
                HttpMethod.DELETE,
                HttpEntity<Void>(adminHeaders()),
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }
    }

    private fun adminHeaders(): HttpHeaders = HttpHeaders().apply {
        set("X-Loopers-Ldap", "loopers.admin")
    }

    private fun adminJsonHeaders(): HttpHeaders = adminHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }

    private fun getBrands(page: Int? = null, size: Int? = null) =
        testRestTemplate.exchange(
            buildString {
                append("/api-admin/v1/brands")
                val params = listOfNotNull(page?.let { "page=$it" }, size?.let { "size=$it" })
                if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
            },
            HttpMethod.GET,
            HttpEntity<Void>(adminHeaders()),
            object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandsResponse>>() {},
        )
}
