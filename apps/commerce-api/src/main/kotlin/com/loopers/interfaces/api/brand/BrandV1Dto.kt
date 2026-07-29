package com.loopers.interfaces.api.brand

import com.loopers.application.brand.command.RegisterBrandCommand
import com.loopers.application.brand.command.UpdateBrandCommand
import com.loopers.application.brand.result.AdminBrandResult
import com.loopers.application.brand.result.BrandResult
import com.loopers.support.page.PageResult

class BrandV1Dto {
    data class RegisterBrandRequest(
        val name: String,
    ) {
        fun toCommand(): RegisterBrandCommand = RegisterBrandCommand(name = name)
    }

    data class UpdateBrandRequest(
        val name: String,
    ) {
        fun toCommand(): UpdateBrandCommand = UpdateBrandCommand(name = name)
    }

    data class BrandResponse(
        val id: Long,
        val name: String,
    ) {
        companion object {
            fun from(result: BrandResult): BrandResponse = BrandResponse(id = result.id, name = result.name)
        }
    }

    data class AdminBrandResponse(
        val id: Long,
        val name: String,
    ) {
        companion object {
            fun from(result: AdminBrandResult): AdminBrandResponse =
                AdminBrandResponse(id = result.id, name = result.name)
        }
    }

    data class BrandsResponse(
        val content: List<AdminBrandResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<AdminBrandResult>): BrandsResponse = BrandsResponse(
                content = page.content.map { AdminBrandResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }
}
