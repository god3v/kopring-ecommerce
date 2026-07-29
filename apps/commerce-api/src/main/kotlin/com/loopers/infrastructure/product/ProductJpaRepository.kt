package com.loopers.infrastructure.product

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findAllByBrandId(brandId: Long, pageable: Pageable): Page<ProductEntity>

    fun findAllByBrandId(brandId: Long): List<ProductEntity>

    fun existsByBrandIdAndName(brandId: Long, name: String): Boolean

    // 재고 차감 경로 전용 — 비관 쓰기 락. id 순 정렬로 락 획득 순서를 고정해 데드락을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM ProductEntity p WHERE p.id IN :ids ORDER BY p.id")
    fun findAllByIdInForUpdate(@Param("ids") ids: Collection<Long>): List<ProductEntity>

    // 보상(재고 복원) 경로 전용 — soft-delete 된 상품도 잠가야 해, @SQLRestriction 을 우회하는 native FOR UPDATE 로 조회한다.
    // 차감 경로와 같은 id 순 정렬로 데드락을 막는다.
    @Query(
        value = "SELECT * FROM products WHERE id IN (:ids) ORDER BY id FOR UPDATE",
        nativeQuery = true,
    )
    fun findAllByIdInForUpdateIncludingDeleted(@Param("ids") ids: Collection<Long>): List<ProductEntity>

    // 좋아요 수 원자적 증감 — DB 가 직렬화한다(lost update 방지). 감소는 0 미만으로 내려가지 않는다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.likeCount = p.likeCount + 1 WHERE p.id = :id")
    fun increaseLikeCount(@Param("id") id: Long): Int

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ProductEntity p SET p.likeCount = p.likeCount - 1 WHERE p.id = :id AND p.likeCount > 0")
    fun decreaseLikeCount(@Param("id") id: Long): Int
}
