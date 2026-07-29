package com.loopers.interfaces.api.product

import com.loopers.application.product.command.RegisterProductCommand
import com.loopers.application.product.command.UpdateProductCommand
import com.loopers.application.product.result.AdminProductDetailResult
import com.loopers.application.product.result.AdminProductSummaryResult
import com.loopers.application.product.result.ProductDetailResult
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.domain.product.SalesStatus
import com.loopers.support.page.PageResult

class ProductV1Dto {
    // ── 회원 채널 ────────────────────────────────────────────────────────────

    data class ProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brandId: Long,
    ) {
        companion object {
            fun from(result: ProductSummaryResult): ProductResponse = ProductResponse(
                id = result.id,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
            )
        }
    }

    data class ProductsResponse(
        val content: List<ProductResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<ProductSummaryResult>): ProductsResponse = ProductsResponse(
                content = page.content.map { ProductResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class ProductDetailResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brandId: Long,
        val brandName: String,
        val likedByMe: Boolean,
        // 오늘 랭킹판 순위(1부터). 랭킹판에 없으면 null.
        val rank: Long?,
    ) {
        companion object {
            fun from(result: ProductDetailResult): ProductDetailResponse = ProductDetailResponse(
                id = result.id,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
                brandName = result.brandName,
                likedByMe = result.likedByMe,
                rank = result.rank,
            )
        }
    }

    // ── 관리자 채널 ──────────────────────────────────────────────────────────

    data class RegisterProductRequest(
        val brandId: Long,
        val name: String,
        val price: Long,
        val stock: Int,
    ) {
        fun toCommand(): RegisterProductCommand = RegisterProductCommand(
            brandId = brandId,
            name = name,
            price = price,
            stock = stock,
        )
    }

    data class UpdateProductRequest(
        val name: String,
        val price: Long,
        val salesStatus: String,
    ) {
        // salesStatus 는 와이어 key(snake_case) → SalesStatus 로 역매핑한다. 미지원 값은 PRODUCT_BAD_REQUEST.
        fun toCommand(): UpdateProductCommand = UpdateProductCommand(
            name = name,
            price = price,
            salesStatus = SalesStatus.from(salesStatus),
        )
    }

    data class AdminProductResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brandId: Long,
        val salesStatus: String,
    ) {
        companion object {
            fun from(result: AdminProductSummaryResult): AdminProductResponse = AdminProductResponse(
                id = result.id,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
                // 와이어 값은 SalesStatus.key(snake_case) — 기본 enum 직렬화(ON_SALE)와 다르다.
                salesStatus = result.salesStatus.key,
            )
        }
    }

    data class AdminProductsResponse(
        val content: List<AdminProductResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<AdminProductSummaryResult>): AdminProductsResponse = AdminProductsResponse(
                content = page.content.map { AdminProductResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class AdminProductDetailResponse(
        val id: Long,
        val name: String,
        val price: Long,
        val likeCount: Long,
        val brandId: Long,
        val brandName: String,
        val salesStatus: String,
    ) {
        companion object {
            fun from(result: AdminProductDetailResult): AdminProductDetailResponse = AdminProductDetailResponse(
                id = result.id,
                name = result.name,
                price = result.price,
                likeCount = result.likeCount,
                brandId = result.brandId,
                brandName = result.brandName,
                salesStatus = result.salesStatus.key,
            )
        }
    }
}
