package com.loopers.infrastructure.coupon

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCouponJpaRepository : JpaRepository<UserCouponEntity, Long> {
    fun existsByUserIdAndCouponId(userId: Long, couponId: Long): Boolean
    fun findAllByUserId(userId: Long, pageable: Pageable): Page<UserCouponEntity>
    fun findAllByCouponId(couponId: Long, pageable: Pageable): Page<UserCouponEntity>

    /** 사용 경로 전용 비관적 쓰기 락 조회 — 동시 사용 직렬화로 이중 소진 방지. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select uc from UserCouponEntity uc where uc.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): UserCouponEntity?
}
