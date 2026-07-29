package com.loopers.infrastructure.like

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface LikeJpaRepository : JpaRepository<LikeEntity, Long> {
    fun findByUserIdAndProductId(userId: Long, productId: Long): LikeEntity?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM LikeEntity l WHERE l.userId = :userId AND l.productId = :productId")
    fun deleteByUserIdAndProductId(@Param("userId") userId: Long, @Param("productId") productId: Long): Int

    fun findAllByUserId(userId: Long, pageable: Pageable): Page<LikeEntity>
}
