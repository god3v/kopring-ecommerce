package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.BatchSize
import java.time.LocalDateTime

@Entity
@Table(
    name = "orders",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_orders_user_idem", columnNames = ["user_id", "idempotency_key"]),
    ],
)
class OrderEntity private constructor(
    userId: Long,
    orderedAt: LocalDateTime,
    idempotencyKey: String,
    userCouponId: Long?,
    discountAmount: Long,
    status: OrderStatus,
    paymentTransactionId: String?,
    paymentResultCode: String?,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "ordered_at", nullable = false)
    var orderedAt: LocalDateTime = orderedAt
        protected set

    @Column(name = "idempotency_key", nullable = false)
    var idempotencyKey: String = idempotencyKey
        protected set

    @Column(name = "user_coupon_id")
    var userCouponId: Long? = userCouponId
        protected set

    @Column(name = "discount_amount", nullable = false)
    var discountAmount: Long = discountAmount
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: OrderStatus = status
        protected set

    @Column(name = "payment_transaction_id")
    var paymentTransactionId: String? = paymentTransactionId
        protected set

    @Column(name = "payment_result_code")
    var paymentResultCode: String? = paymentResultCode
        protected set

    // 목록 조회 시 lines 지연 로딩 N+1 을 피한다 — 페이지의 주문들을 IN 절로 모아 적재(페이징 호환).
    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL], orphanRemoval = true)
    @BatchSize(size = 100)
    var lines: MutableList<OrderLineEntity> = mutableListOf()
        protected set

    fun toDomain(): Order = Order(
        id = this.id,
        userId = this.userId,
        lines = this.lines.map { it.toDomain() },
        orderedAt = this.orderedAt,
        idempotencyKey = this.idempotencyKey,
        userCouponId = this.userCouponId,
        discountAmount = this.discountAmount,
        status = this.status,
        paymentTransactionId = this.paymentTransactionId,
        paymentResultCode = this.paymentResultCode,
    )

    fun syncFrom(order: Order) {
        this.status = order.status
        this.paymentTransactionId = order.paymentTransactionId
        this.paymentResultCode = order.paymentResultCode
    }

    companion object {
        fun from(order: Order): OrderEntity {
            val entity = OrderEntity(
                userId = order.userId,
                orderedAt = order.orderedAt,
                idempotencyKey = order.idempotencyKey,
                userCouponId = order.userCouponId,
                discountAmount = order.discountAmount,
                status = order.status,
                paymentTransactionId = order.paymentTransactionId,
                paymentResultCode = order.paymentResultCode,
            )
            order.lines.forEach { line ->
                entity.lines.add(OrderLineEntity.from(line, entity))
            }
            return entity
        }
    }
}
