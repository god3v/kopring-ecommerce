package com.loopers.infrastructure.payment

import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    // 정산·취소 경로 전용 — 비관적 쓰기 락(SELECT ... FOR UPDATE). 동시/중복 정산의 상태 전이를 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): PaymentEntity?

    // 주문의 최신 결제 1건(id 내림차순). 진행 중 결제 dedupe 에 사용.
    fun findFirstByOrderIdOrderByIdDesc(orderId: Long): PaymentEntity?

    // 외부 거래 식별자로 결제 1건. 콜백·폴링 정산 매칭에 사용(활성 행 한정 유일).
    fun findByTransactionId(transactionId: String): PaymentEntity?

    // 정산 경로 전용 — 거래 식별자로 비관적 쓰기 락(SELECT ... FOR UPDATE). 콜백·폴링 동시/중복 정산을 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.transactionId = :transactionId")
    fun findByTransactionIdForUpdate(@Param("transactionId") transactionId: String): PaymentEntity?

    // 특정 상태의 결제 전체. 처리 중(REQUESTED) 폴링 복구 대상 조회.
    fun findAllByStatus(status: PaymentStatus): List<PaymentEntity>
}
