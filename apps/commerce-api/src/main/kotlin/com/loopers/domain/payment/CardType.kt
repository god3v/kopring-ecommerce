package com.loopers.domain.payment

import com.loopers.support.error.CoreException

/** 외부 PG 가 지원하는 카드사. 지원하지 않는 값은 결제 입력 오류(400)로 거른다. */
enum class CardType {
    SAMSUNG,
    KB,
    HYUNDAI,
    ;

    companion object {
        fun from(raw: String): CardType =
            entries.firstOrNull { it.name == raw }
                ?: throw CoreException(PaymentErrorType.UNSUPPORTED_CARD_TYPE, "지원하지 않는 카드 종류입니다.")
    }
}
