package com.loopers.interfaces.api.product

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Product Admin V1 API", description = "관리자 상품 API")
interface ProductV1AdminApiSpec {
    @Operation(
        summary = "(관리자) 상품 목록 조회",
        description = "삭제 마크되지 않은 상품을 최신순으로 브랜드 필터·페이지 단위 조회합니다. " +
            "각 항목에 판매 상태가 포함됩니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getProducts(
        brandId: Long?,
        pageable: Pageable,
    ): ApiResponse<ProductV1Dto.AdminProductsResponse>

    @Operation(
        summary = "(관리자) 상품 상세 조회",
        description = "상품 식별자로 상세를 조회합니다. 삭제 마크된 상품은 404 입니다. " +
            "응답에 판매 상태가 포함됩니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getProduct(productId: Long): ApiResponse<ProductV1Dto.AdminProductDetailResponse>

    @Operation(
        summary = "(관리자) 상품 등록",
        description = "이미 존재하는 브랜드에 새 상품을 등록합니다. 입력 위반은 400, 브랜드 미존재는 404, " +
            "같은 브랜드 내 이름 중복은 409 입니다. 판매 상태는 on_sale 로 초기화됩니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun registerProduct(
        request: ProductV1Dto.RegisterProductRequest,
    ): ApiResponse<ProductV1Dto.AdminProductDetailResponse>

    @Operation(
        summary = "(관리자) 상품 정보 수정",
        description = "이름·가격·판매 상태를 수정합니다(브랜드는 수정 대상이 아닙니다). 미존재/삭제 마크는 404, " +
            "입력 위반은 400, 같은 브랜드 내 이름 중복은 409 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun updateProduct(
        productId: Long,
        request: ProductV1Dto.UpdateProductRequest,
    ): ApiResponse<ProductV1Dto.AdminProductDetailResponse>

    @Operation(
        summary = "(관리자) 상품 삭제",
        description = "상품을 삭제 마크(soft delete)합니다. 좋아요 행은 유지됩니다. 미존재/이미 삭제 마크는 404 입니다. " +
            "관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun deleteProduct(productId: Long): ApiResponse<Any>
}
