package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DiscountPolicyTest {
    @DisplayName("정책을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정액 금액이 0 이하면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -1, -1000])
        fun throwsWhenFixedAmountNonPositive(amount: Long) {
            val result = assertThrows<CoreException> { FixedAmountDiscountPolicy(amount) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("정률 비율이 1 미만 또는 100 초과면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(longs = [0, -5, 101, 200])
        fun throwsWhenRatePercentOutOfRange(percent: Long) {
            val result = assertThrows<CoreException> { PercentageDiscountPolicy(percent) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }
    }

    @DisplayName("of(type, value) 로 복원할 때, ")
    @Nested
    inner class Of {
        @DisplayName("FIXED 는 FixedAmountDiscountPolicy, RATE 는 PercentageDiscountPolicy 로 복원된다.")
        @Test
        fun restoresSubtype() {
            val fixed = DiscountPolicy.of(DiscountType.FIXED, 5000)
            val rate = DiscountPolicy.of(DiscountType.RATE, 10)

            assertThat(fixed).isEqualTo(FixedAmountDiscountPolicy(5000))
            assertThat(rate).isEqualTo(PercentageDiscountPolicy(10))
        }
    }

    @DisplayName("discountFor 를 계산할 때, ")
    @Nested
    inner class DiscountFor {
        @DisplayName("정액은 할인 금액이지만 주문 합계를 초과하지 않는다.")
        @Test
        fun fixedCapsAtOrderAmount() {
            val fixed = FixedAmountDiscountPolicy(5000)

            assertThat(fixed.discountFor(10000)).isEqualTo(5000)
            assertThat(fixed.discountFor(3000)).isEqualTo(3000)
        }

        @DisplayName("정률은 주문 합계 × 비율 / 100 을 버림한 값이다.")
        @Test
        fun rateFloors() {
            val rate = PercentageDiscountPolicy(10)

            // 9999 * 10 / 100 = 999.9 -> 999 (버림)
            assertThat(rate.discountFor(9999)).isEqualTo(999)
            assertThat(rate.discountFor(10000)).isEqualTo(1000)
        }

        @DisplayName("정률 100% 는 주문 합계를 초과하지 않는다.")
        @Test
        fun rateDoesNotExceedOrderAmount() {
            assertThat(PercentageDiscountPolicy(100).discountFor(7000)).isEqualTo(7000)
        }

        @DisplayName("어떤 정책이든 할인 금액은 음수가 아니다.")
        @Test
        fun neverNegative() {
            assertThat(FixedAmountDiscountPolicy(5000).discountFor(0)).isEqualTo(0)
            assertThat(PercentageDiscountPolicy(10).discountFor(0)).isEqualTo(0)
        }

        @DisplayName("정률은 매우 큰 주문 합계에서도 오버플로로 0 이 되지 않고 floor(합계 × 비율 / 100) 을 유지한다.")
        @Test
        fun rateDoesNotOverflowOnHugeOrderAmount() {
            // orderAmount * percent 가 Long 오버플로를 일으키는 크기(1e18 * 10 > Long.MAX) 에서도 정확해야 한다.
            val orderAmount = 1_000_000_000_000_000_000L // 1e18

            assertThat(PercentageDiscountPolicy(10).discountFor(orderAmount)).isEqualTo(100_000_000_000_000_000L) // 1e17
        }
    }
}
