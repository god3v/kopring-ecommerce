package com.loopers.domain.brand

import com.loopers.support.page.PageResult

interface BrandRepository {
    fun save(brand: Brand): Brand
    fun findById(id: Long): Brand?
    fun findAllByIds(ids: Collection<Long>): List<Brand>
    fun findAll(page: Int, size: Int): PageResult<Brand>
    fun existsByName(name: String): Boolean
}
