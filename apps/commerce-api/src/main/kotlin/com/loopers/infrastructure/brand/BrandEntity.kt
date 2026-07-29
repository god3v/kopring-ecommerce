package com.loopers.infrastructure.brand

import com.loopers.domain.BaseEntity
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandName
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(name = "brands")
@SQLRestriction("deleted_at IS NULL")
class BrandEntity private constructor(
    name: String,
) : BaseEntity() {
    @Column(nullable = false)
    var name: String = name
        protected set

    fun toDomain(): Brand = Brand(id = this.id, name = BrandName.of(this.name))

    fun syncFrom(brand: Brand) {
        this.name = brand.name.value
        if (brand.isDeleted() && this.deletedAt == null) {
            this.delete()
        }
    }

    companion object {
        fun from(brand: Brand): BrandEntity = BrandEntity(name = brand.name.value)
    }
}
