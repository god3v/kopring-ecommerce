package com.loopers.interfaces.api.brand

import com.loopers.application.brand.BrandFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.page.PageQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 브랜드 API. 경로(api-admin)는 `AdminAuthInterceptor` 가 `X-Loopers-Ldap` 헤더로 인증한다.
 */
@RestController
@RequestMapping("/api-admin/v1/brands")
class BrandV1AdminController(
    private val brandFacade: BrandFacade,
) : BrandV1AdminApiSpec {
    @GetMapping
    override fun getBrands(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<BrandV1Dto.BrandsResponse> {
        val result = brandFacade.getBrandsForAdmin(PageQuery(page = pageable.pageNumber, size = pageable.pageSize))
        return ApiResponse.success(BrandV1Dto.BrandsResponse.from(result))
    }

    @GetMapping("/{brandId}")
    override fun getBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<BrandV1Dto.AdminBrandResponse> {
        val result = brandFacade.getBrandForAdmin(brandId)
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(result))
    }

    @PostMapping
    override fun registerBrand(
        @RequestBody request: BrandV1Dto.RegisterBrandRequest,
    ): ApiResponse<BrandV1Dto.AdminBrandResponse> {
        val result = brandFacade.registerBrand(request.toCommand())
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(result))
    }

    @PutMapping("/{brandId}")
    override fun updateBrand(
        @PathVariable brandId: Long,
        @RequestBody request: BrandV1Dto.UpdateBrandRequest,
    ): ApiResponse<BrandV1Dto.AdminBrandResponse> {
        val result = brandFacade.updateBrand(brandId, request.toCommand())
        return ApiResponse.success(BrandV1Dto.AdminBrandResponse.from(result))
    }

    @DeleteMapping("/{brandId}")
    override fun deleteBrand(
        @PathVariable brandId: Long,
    ): ApiResponse<Any> {
        brandFacade.deleteBrand(brandId)
        return ApiResponse.success()
    }
}
