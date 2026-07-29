package com.loopers.domain.product

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SalesStatusTest {
    @DisplayName("SalesStatus 는 ON_SALE · OUT_OF_STOCK · OFF_SALE 세 값을 보유한다.")
    @Test
    fun hasThreeAllowedValues() {
        assertThat(SalesStatus.entries).containsExactlyInAnyOrder(
            SalesStatus.ON_SALE,
            SalesStatus.OUT_OF_STOCK,
            SalesStatus.OFF_SALE,
        )
    }

    @DisplayName("from(허용 문자열) 호출 시 대응 enum 으로 매핑된다.")
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "on_sale, ON_SALE",
        "out_of_stock, OUT_OF_STOCK",
        "off_sale, OFF_SALE",
    )
    fun mapsFromKey(key: String, expected: SalesStatus) {
        assertThat(SalesStatus.from(key)).isEqualTo(expected)
    }

    @DisplayName("from(미지원 문자열) 호출 시 PRODUCT_BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["unknown", "ON_SALE", "sale", ""])
    fun throwsException_whenValueIsUnsupported(invalidValue: String) {
        // when
        val result = assertThrows<CoreException> {
            SalesStatus.from(invalidValue)
        }

        // then
        assertThat(result.errorType).isEqualTo(ProductErrorType.PRODUCT_BAD_REQUEST)
    }
}
