package com.loopers.interfaces.api.product

import com.loopers.application.product.ProductFacade
import com.loopers.application.product.query.GetProductsQuery
import com.loopers.domain.product.ProductSortType
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.support.page.PageQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/products")
class ProductV1Controller(
    private val productFacade: ProductFacade,
) : ProductV1ApiSpec {
    @GetMapping
    override fun getProducts(
        @RequestParam(required = false) brandId: Long?,
        @RequestParam(required = false) sort: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<ProductV1Dto.ProductsResponse> {
        val query = GetProductsQuery(
            sort = ProductSortType.from(sort),
            brandId = brandId,
            paging = PageQuery(page = pageable.pageNumber, size = pageable.pageSize.coerceAtMost(MAX_PAGE_SIZE)),
        )
        val result = productFacade.getProducts(query)
        return ApiResponse.success(ProductV1Dto.ProductsResponse.from(result))
    }

    @GetMapping("/{productId}")
    override fun getProduct(
        // 선택 인증: @RequireAuth 없이 nullable AuthUser? — 미인증/인증 실패는 likedByMe=false.
        @LoginUser user: AuthUser?,
        @PathVariable productId: Long,
    ): ApiResponse<ProductV1Dto.ProductDetailResponse> {
        val result = productFacade.getProductDetail(productId, user?.id)
        return ApiResponse.success(ProductV1Dto.ProductDetailResponse.from(result))
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
