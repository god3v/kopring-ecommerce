package com.loopers.infrastructure.payment

import com.loopers.domain.BaseEntity
import com.loopers.domain.payment.Payment
import com.loopers.domain.payment.PaymentStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 결제 엔티티. 한 주문에 여러 결제 시도가 쌓일 수 있어 `order_id` 는 유니크가 아니다(1:N).
 * `transaction_id` 는 활성 행 한정 유일 — 같은 거래의 중복 매핑을 막는다(REQUESTED 는 NULL 이라 다중 허용).
 */
@Entity
@Table(
    name = "payments",
    uniqueConstraints = [UniqueConstraint(name = "uk_payments_transaction_id", columnNames = ["transaction_id"])],
)
@SQLRestriction("deleted_at IS NULL")
class PaymentEntity private constructor(
    orderId: Long,
    amount: Long,
    status: PaymentStatus,
    transactionId: String?,
    failureReason: String?,
    requestedAt: LocalDateTime,
    paidAt: LocalDateTime?,
    canceledAt: LocalDateTime?,
) : BaseEntity() {
    @Column(name = "order_id", nullable = false)
    var orderId: Long = orderId
        protected set

    @Column(nullable = false)
    var amount: Long = amount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = status
        protected set

    @Column(name = "transaction_id")
    var transactionId: String? = transactionId
        protected set

    @Column(name = "failure_reason")
    var failureReason: String? = failureReason
        protected set

    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime = requestedAt
        protected set

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = paidAt
        protected set

    @Column(name = "canceled_at")
    var canceledAt: LocalDateTime? = canceledAt
        protected set

    fun toDomain(): Payment = Payment(
        id = this.id,
        orderId = this.orderId,
        amount = this.amount,
        status = this.status,
        transactionId = this.transactionId,
        failureReason = this.failureReason,
        requestedAt = this.requestedAt,
        paidAt = this.paidAt,
        canceledAt = this.canceledAt,
    )

    fun syncFrom(payment: Payment) {
        this.status = payment.status
        this.transactionId = payment.transactionId
        this.failureReason = payment.failureReason
        this.paidAt = payment.paidAt
        this.canceledAt = payment.canceledAt
    }

    companion object {
        fun from(payment: Payment): PaymentEntity = PaymentEntity(
            orderId = payment.orderId,
            amount = payment.amount,
            status = payment.status,
            transactionId = payment.transactionId,
            failureReason = payment.failureReason,
            requestedAt = payment.requestedAt,
            paidAt = payment.paidAt,
            canceledAt = payment.canceledAt,
        )
    }
}
