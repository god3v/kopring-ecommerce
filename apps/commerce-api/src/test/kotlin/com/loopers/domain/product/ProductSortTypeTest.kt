package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ProductSortTypeTest {
    @DisplayName("from(허용 문자열) 호출 시 대응 enum 으로 매핑된다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "latest, LATEST",
        "price_asc, PRICE_ASC",
        "likes_desc, LIKES_DESC",
    )
    fun mapsFromKey(key: String, expected: ProductSortType) {
        assertThat(ProductSortType.from(key)).isEqualTo(expected)
    }

    @DisplayName("from(null) 호출 시 기본값 LATEST 가 반환된다.")
    @Test
    fun returnsLatest_whenValueIsNull() {
        assertThat(ProductSortType.from(null)).isEqualTo(ProductSortType.LATEST)
    }

    @DisplayName("from(미지원 문자열) 호출 시 PRODUCT_BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["unknown", "LATEST", "name_asc", ""])
    fun throwsException_whenValueIsUnsupported(invalidValue: String) {
        // when
        val result = assertThrows<CoreException> {
            ProductSortType.from(invalidValue)
        }

        // then
        assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
    }
}
