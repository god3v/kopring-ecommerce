package com.loopers.interfaces.api

import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
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
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val brandRepository: BrandRepository,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @DisplayName("GET /api/v1/brands/{brandId} 단일 브랜드 조회 (인증 불필요)")
    @Nested
    inner class GetBrand {
        @DisplayName("존재하는 브랜드를 조회하면, 200 과 { id, name } 을 받는다.")
        @Test
        fun returnsBrand_whenExists() {
            val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id

            val response = testRestTemplate.exchange(
                "/api/v1/brands/$brandId",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                object : ParameterizedTypeReference<ApiResponse<BrandV1Dto.BrandResponse>>() {},
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
                "/api/v1/brands/999999",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }

        @DisplayName("삭제 마크된 브랜드를 조회하면, 404 BRAND_NOT_FOUND 응답을 받는다 (회원에게 노출되지 않는다).")
        @Test
        fun returnsNotFound_whenSoftDeleted() {
            val brand = brandRepository.save(BrandFixture.validBrand("나이키"))
            brand.softDelete()
            brandRepository.save(brand)

            val response = testRestTemplate.exchange(
                "/api/v1/brands/${brand.id}",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                object : ParameterizedTypeReference<ApiResponse<Any>>() {},
            )

            assertAll(
                { assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND) },
                { assertThat(response.body?.meta?.errorCode).isEqualTo("BRAND_NOT_FOUND") },
            )
        }
    }
}
