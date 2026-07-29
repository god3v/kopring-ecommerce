package com.loopers.interfaces.api.brand

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Brand V1 API", description = "Brand 조회 API")
interface BrandV1ApiSpec {
    @Operation(
        summary = "단일 브랜드 조회",
        description = "브랜드 식별자로 단일 브랜드 정보를 조회합니다. 인증이 필요하지 않습니다.",
    )
    fun getBrand(brandId: Long): ApiResponse<BrandV1Dto.BrandResponse>
}
