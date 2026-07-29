package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 상품 API. 경로(api-admin)는 AdminAuthInterceptor 가 X-Loopers-Ldap 헤더로 인증한다.
 */
@RestController
@RequestMapping("/api-admin/v1/products")
class ProductV1AdminController(
    private val productFacade: ProductFacade,
) : ProductV1AdminApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<ProductV1Dto.AdminProductsResponse> {
        val result = productFacade.getProductsForAdmin(
            brandId,
            PageQuery(page = pageable.pageNumber, size = pageable.pageSize),
        )
        return ApiResponse.success(ProductV1Dto.AdminProductsResponse.from(result))
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.AdminProductDetailResponse> {
        val result = productFacade.getProductForAdmin(productId)
        return ApiResponse.success(ProductV1Dto.AdminProductDetailResponse.from(result))
    }

    @PostMapping
    override fun registerProduct(
        @RequestBody request: ProductV1Dto.RegisterProductRequest,
    ): ApiResponse<ProductV1Dto.AdminProductDetailResponse> {
        val result = productFacade.registerProduct(request.toCommand())
        return ApiResponse.success(ProductV1Dto.AdminProductDetailResponse.from(result))
    }

    @PutMapping("/{productId}")
    override fun updateProduct(
        @PathVariable productId: Long,
        @RequestBody request: ProductV1Dto.UpdateProductRequest,
    ): ApiResponse<ProductV1Dto.AdminProductDetailResponse> {
        val result = productFacade.updateProduct(productId, request.toCommand())
        return ApiResponse.success(ProductV1Dto.AdminProductDetailResponse.from(result))
    }

    @DeleteMapping("/{productId}")
    override fun deleteProduct(
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        productFacade.deleteProduct(productId)
        return ApiResponse.success()
    }
}
