package com.loopers.domain.product

import com.loopers.support.error.ErrorStatus
import com.loopers.support.error.ErrorType

enum class ProductErrorType(
    override val status: ErrorStatus,
    override val code: String,
    override val message: String,
) : ErrorType {
    PRODUCT_BAD_REQUEST(ErrorStatus.BAD_REQUEST, "PRODUCT_BAD_REQUEST", "상품 입력이 올바르지 않습니다."),
    INSUFFICIENT_STOCK(ErrorStatus.CONFLICT, "INSUFFICIENT_STOCK", "재고가 부족합니다."),
    PRODUCT_NOT_FOUND(ErrorStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다."),
    DUPLICATE_PRODUCT_NAME(ErrorStatus.CONFLICT, "DUPLICATE_PRODUCT_NAME", "같은 브랜드 안에 같은 이름의 상품이 이미 존재합니다."),
}
