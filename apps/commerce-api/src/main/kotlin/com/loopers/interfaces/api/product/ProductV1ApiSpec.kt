package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Product V1 API", description = "상품 카탈로그 조회 API (회원)")
interface ProductV1ApiSpec {
    @Operation(
        summary = "상품 목록 조회",
        description = "삭제 마크되지 않은 상품을 정렬·브랜드 필터·페이지 단위로 조회합니다. " +
            "정렬은 latest(기본)·price_asc·likes_desc 중 하나이며 그 외 값은 400 입니다. 인증이 필요하지 않습니다.",
    )
    fun getProducts(
        brandId: Long?,
        sort: String?,
        pageable: Pageable,
    ): ApiResponse<ProductV1Dto.ProductsResponse>

    @Operation(
        summary = "상품 상세 조회",
        description = "상품 식별자로 상세를 조회합니다. 삭제 마크된 상품은 404 입니다. " +
            "인증 헤더가 함께 오면 likedByMe 가 채워지며, 미인증/인증 실패는 거부 없이 likedByMe=false 로 응답합니다.",
    )
    fun getProduct(
        user: AuthUser?,
        productId: Long,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse>
}
