package com.loopers.domain.payment

import com.loopers.support.error.CoreException

/**
 * 카드 번호 — `xxxx-xxxx-xxxx-xxxx`(숫자 4자리 × 4, 하이픈 구분).
 * 우리 시스템에 저장·로깅하지 않고 외부 PG 로만 전달한다 — 예외 메시지에도 값을 싣지 않는다.
 */
@JvmInline
value class CardNumber(val value: String) {
    init {
        if (!FORMAT.matches(value)) {
            throw CoreException(PaymentErrorType.INVALID_CARD_NUMBER, "카드 번호 형식이 올바르지 않습니다.")
        }
    }

    companion object {
        private val FORMAT = Regex("""\d{4}-\d{4}-\d{4}-\d{4}""")
    }
}
