package com.loopers.domain.order

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class QuantityTest {
    @DisplayName("Quantity 를 생성할 때, ")
    @Nested
    inner class Of {
        @DisplayName("value 가 1 이면 통과한다.")
        @Test
        fun passes_whenValueIsOne() {
            // when
            val quantity = Quantity.of(1)

            // then
            assertThat(quantity.value).isEqualTo(1)
        }

        @DisplayName("value 가 1 미만이면 INVALID_QUANTITY 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1, -10])
        fun throwsInvalidQuantity_whenValueIsLessThanOne(invalidValue: Int) {
            // when
            val result = assertThrows<CoreException> {
                Quantity.of(invalidValue)
            }

            // then
            assertThat(result.errorType).isEqualTo(OrderErrorType.INVALID_QUANTITY)
        }
    }
}
