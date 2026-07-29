package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CouponNameTest {
    @DisplayName("blank 이름으로 생성하면 COUPON_BAD_REQUEST 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = ["", " ", "   "])
    fun throwsWhenBlank(blank: String) {
        val result = assertThrows<CoreException> { CouponName.of(blank) }
        assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
    }

    @DisplayName("정상 이름은 값이 보존된다.")
    @Test
    fun preservesValue() {
        assertThat(CouponName.of("신규가입 10% 할인").value).isEqualTo("신규가입 10% 할인")
    }
}
