package com.loopers.domain.brand

import com.loopers.support.error.CoreException

@JvmInline
value class BrandName private constructor(val value: String) {
    companion object {
        fun of(value: String): BrandName {
            if (value.isBlank()) {
                throw CoreException(BrandErrorType.BRAND_BAD_REQUEST, "name 은 비어 있을 수 없다.")
            }
            return BrandName(value)
        }
    }
}
