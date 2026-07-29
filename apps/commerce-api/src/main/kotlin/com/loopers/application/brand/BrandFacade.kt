package com.loopers.application.brand

import com.loopers.application.brand.command.RegisterBrandCommand
import com.loopers.application.brand.command.UpdateBrandCommand
import com.loopers.domain.brand.BrandRepository
import com.loopers.application.brand.result.AdminBrandResult
import com.loopers.application.brand.result.BrandResult
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorType
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class BrandFacade(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
) {
    @Transactional(readOnly = true)
    fun getBrand(brandId: Long): BrandResult {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        return BrandResult.from(brand)
    }

    @Transactional(readOnly = true)
    fun getBrandsForAdmin(pageQuery: PageQuery): PageResult<AdminBrandResult> =
        brandRepository.findAll(pageQuery.page, pageQuery.size).map { AdminBrandResult.from(it) }

    @Transactional(readOnly = true)
    fun getBrandForAdmin(brandId: Long): AdminBrandResult {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        return AdminBrandResult.from(brand)
    }

    @Transactional
    fun registerBrand(command: RegisterBrandCommand): AdminBrandResult {
        if (brandRepository.existsByName(command.name)) {
            throw CoreException(BrandErrorType.DUPLICATE_BRAND_NAME)
        }
        val brand = Brand.create(name = command.name)
        return AdminBrandResult.from(brandRepository.save(brand))
    }

    @Transactional
    fun updateBrand(brandId: Long, command: UpdateBrandCommand): AdminBrandResult {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        if (brand.name.value != command.name && brandRepository.existsByName(command.name)) {
            throw CoreException(BrandErrorType.DUPLICATE_BRAND_NAME)
        }
        brand.update(command.name)
        return AdminBrandResult.from(brandRepository.save(brand))
    }

    @Transactional
    fun deleteBrand(brandId: Long) {
        val brand = brandRepository.findById(brandId)
            ?: throw CoreException(BrandErrorType.BRAND_NOT_FOUND)
        val products = productRepository.findAllByBrandId(brandId)
        products.forEach { it.softDelete() }
        productRepository.saveAll(products)
        brand.softDelete()
        brandRepository.save(brand)
    }
}
