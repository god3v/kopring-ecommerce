package com.loopers.interfaces.api.product

import com.loopers.application.product.result.AdminProductDetailResult
import com.loopers.application.product.result.AdminProductSummaryResult
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.SalesStatus
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ProductV1Dto — salesStatus key 직렬화/역직렬화")
class ProductV1DtoTest {
    @Nested
    @DisplayName("UpdateProductRequest.toCommand — 요청 key → SalesStatus 역매핑")
    inner class UpdateRequestToCommand {
        @Test
        @DisplayName("지원하는 key 는 대응 SalesStatus 로 역매핑된다")
        fun mapsSupportedKey() {
            val command = ProductV1Dto.UpdateProductRequest(
                name = "운동화",
                price = 1_000L,
                salesStatus = "off_sale",
            ).toCommand()

            assertThat(command.salesStatus).isEqualTo(SalesStatus.OFF_SALE)
        }

        @Test
        @DisplayName("지원하지 않는 key 는 PRODUCT_BAD_REQUEST 예외가 발생한다")
        fun throwsOnUnsupportedKey() {
            val request = ProductV1Dto.UpdateProductRequest(
                name = "운동화",
                price = 1_000L,
                salesStatus = "unknown",
            )

            val ex = assertThrows<CoreException> { request.toCommand() }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
        }
    }

    @Nested
    @DisplayName("응답 DTO — SalesStatus → key 직렬화")
    inner class ResponseSerialization {
        @Test
        @DisplayName("AdminProductResponse 는 salesStatus 를 key(snake_case)로 노출한다")
        fun summaryUsesKey() {
            val result = AdminProductSummaryResult(
                id = 1L,
                name = "운동화",
                price = 1_000L,
                likeCount = 0L,
                brandId = 7L,
                salesStatus = SalesStatus.OUT_OF_STOCK,
            )

            assertThat(ProductV1Dto.AdminProductResponse.from(result).salesStatus).isEqualTo("out_of_stock")
        }

        @Test
        @DisplayName("AdminProductDetailResponse 는 salesStatus 를 key(snake_case)로 노출한다")
        fun detailUsesKey() {
            val result = AdminProductDetailResult(
                id = 1L,
                name = "운동화",
                price = 1_000L,
                likeCount = 0L,
                brandId = 7L,
                brandName = "나이키",
                salesStatus = SalesStatus.OFF_SALE,
            )

            assertThat(ProductV1Dto.AdminProductDetailResponse.from(result).salesStatus).isEqualTo("off_sale")
        }
    }
}
