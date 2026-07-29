package com.loopers.infrastructure.brand

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface BrandJpaRepository : JpaRepository<BrandEntity, Long> {
    fun findAllBy(pageable: Pageable): Page<BrandEntity>
    fun existsByName(name: String): Boolean
}
