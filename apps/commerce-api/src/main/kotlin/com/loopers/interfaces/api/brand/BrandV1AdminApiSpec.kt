package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Brand Admin V1 API", description = "관리자 브랜드 API")
interface BrandV1AdminApiSpec {
    @Operation(
        summary = "(관리자) 브랜드 목록 조회",
        description = "삭제 마크되지 않은 브랜드를 최신순으로 페이지 단위 조회합니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getBrands(pageable: Pageable): ApiResponse<BrandV1Dto.BrandsResponse>

    @Operation(
        summary = "(관리자) 브랜드 상세 조회",
        description = "브랜드 식별자로 단일 브랜드를 조회합니다. 삭제 마크된 브랜드는 찾을 수 없음으로 응답합니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getBrand(brandId: Long): ApiResponse<BrandV1Dto.AdminBrandResponse>

    @Operation(
        summary = "(관리자) 브랜드 등록",
        description = "새 브랜드를 등록합니다. 이름이 비어 있으면 400, 기존 브랜드와 중복되면 409 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun registerBrand(request: BrandV1Dto.RegisterBrandRequest): ApiResponse<BrandV1Dto.AdminBrandResponse>

    @Operation(
        summary = "(관리자) 브랜드 정보 수정",
        description = "브랜드 이름을 수정합니다. 미존재/삭제 마크는 404, 이름 비어 있으면 400, 다른 브랜드와 중복되면 409 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun updateBrand(brandId: Long, request: BrandV1Dto.UpdateBrandRequest): ApiResponse<BrandV1Dto.AdminBrandResponse>

    @Operation(
        summary = "(관리자) 브랜드 삭제 (카스케이드)",
        description = "브랜드와 소속 상품을 같은 트랜잭션에서 삭제 마크합니다. 미존재/이미 삭제 마크는 404 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun deleteBrand(brandId: Long): ApiResponse<Any>
}
