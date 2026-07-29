package com.loopers.domain.order

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OrderLineTest {
    @DisplayName("OrderLine 을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 인자로 생성하면 productId · productName · unitPrice · quantity 가 그대로 보관된다.")
        @Test
        fun preservesFields_whenArgumentsAreValid() {
            // when
            val line = OrderLine.create(
                productId = 1L,
                productName = "T-shirt",
                unitPrice = 1000,
                quantity = 2,
            )

            // then
            assertThat(line.productId).isEqualTo(1L)
            assertThat(line.productName).isEqualTo("T-shirt")
            assertThat(line.unitPrice).isEqualTo(1000)
            assertThat(line.quantity.value).isEqualTo(2)
        }

        @DisplayName("productName 이 blank 면 LINE_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = ["", " ", "   "])
        fun throwsLineBadRequest_whenProductNameIsBlank(blankName: String) {
            // when
            val result = assertThrows<CoreException> {
                OrderLine.create(
                    productId = 1L,
                    productName = blankName,
                    unitPrice = 1000,
                    quantity = 1,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(OrderErrorType.LINE_BAD_REQUEST)
        }

        @DisplayName("unitPrice 가 음수면 LINE_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsLineBadRequest_whenUnitPriceIsNegative() {
            // when
            val result = assertThrows<CoreException> {
                OrderLine.create(
                    productId = 1L,
                    productName = "T-shirt",
                    unitPrice = -1,
                    quantity = 1,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(OrderErrorType.LINE_BAD_REQUEST)
        }

        @DisplayName("quantity 가 1 미만이면 Quantity 의 INVALID_QUANTITY 예외로 위임된다.")
        @ParameterizedTest
        @ValueSource(ints = [0, -1])
        fun throwsInvalidQuantity_whenQuantityIsLessThanOne(invalidQuantity: Int) {
            // when
            val result = assertThrows<CoreException> {
                OrderLine.create(
                    productId = 1L,
                    productName = "T-shirt",
                    unitPrice = 1000,
                    quantity = invalidQuantity,
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(OrderErrorType.INVALID_QUANTITY)
        }
    }

    @DisplayName("subtotal 은 unitPrice × quantity 로 계산된다.")
    @Test
    fun subtotalEqualsUnitPriceTimesQuantity() {
        // given
        val line = OrderLine.create(
            productId = 1L,
            productName = "T-shirt",
            unitPrice = 1500,
            quantity = 3,
        )

        // when
        val subtotal = line.subtotal

        // then
        assertThat(subtotal).isEqualTo(4500)
    }
}
