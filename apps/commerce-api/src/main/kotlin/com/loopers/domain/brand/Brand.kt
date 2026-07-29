package com.loopers.domain.brand

import java.time.LocalDateTime

class Brand internal constructor(
    val id: Long = 0L,
    name: BrandName,
) {
    var name: BrandName = name
        private set

    var deletedAt: LocalDateTime? = null
        private set

    fun softDelete() {
        if (deletedAt == null) {
            deletedAt = LocalDateTime.now()
        }
    }

    fun isDeleted(): Boolean = deletedAt != null

    fun update(name: String) {
        this.name = BrandName.of(name)
    }

    companion object {
        fun create(name: String): Brand = Brand(name = BrandName.of(name))
    }
}
