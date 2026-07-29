package com.loopers.infrastructure.metrics

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductMetricsJpaRepository : JpaRepository<ProductMetricsEntity, Long> {
    fun findByProductId(productId: Long): ProductMetricsEntity?

    /** 증분 반영 전용 비관적 쓰기 락 조회 — 동시 증분의 lost update 를 막는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from ProductMetricsEntity m where m.productId = :productId")
    fun findByProductIdForUpdate(@Param("productId") productId: Long): ProductMetricsEntity?
}
