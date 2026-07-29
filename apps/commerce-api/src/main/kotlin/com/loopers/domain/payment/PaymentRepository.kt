package com.loopers.domain.payment

interface PaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?

    /** 결제 정산·취소 경로 전용 — 비관적 쓰기 락으로 조회한다. 동시 정산 직렬화로 이중 처리 방지. */
    fun findByIdForUpdate(id: Long): Payment?

    /** 주문의 가장 최근 결제 — 진행 중(REQUESTED/APPROVED) 결제 중복 생성 방어용(1주문 N시도). */
    fun findLatestByOrderId(orderId: Long): Payment?

    /** 외부 거래 식별자로 결제 조회 — 콜백·폴링 정산의 매칭 기준(접수 시 기록된 transactionId). */
    fun findByTransactionId(transactionId: String): Payment?

    /** 정산 경로 전용 — 거래 식별자로 비관적 쓰기 락 조회. 콜백·폴링 동시 도착 시 정산을 직렬화한다. */
    fun findByTransactionIdForUpdate(transactionId: String): Payment?

    /** 특정 상태의 결제 전체 — 처리 중(REQUESTED) 결제 폴링 복구 대상 조회용. */
    fun findAllByStatus(status: PaymentStatus): List<Payment>
}
