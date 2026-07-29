package com.loopers.infrastructure.order

import com.loopers.domain.BaseEntity
import com.loopers.domain.order.OrderLine
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "order_lines")
class OrderLineEntity private constructor(
    productId: Long,
    productName: String,
    unitPrice: Long,
    quantity: Int,
    order: OrderEntity,
) : BaseEntity() {
    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    @Column(name = "product_name", nullable = false)
    var productName: String = productName
        protected set

    @Column(name = "unit_price", nullable = false)
    var unitPrice: Long = unitPrice
        protected set

    @Column(name = "quantity", nullable = false)
    var quantity: Int = quantity
        protected set

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    var order: OrderEntity = order
        protected set

    fun toDomain(): OrderLine = OrderLine.create(
        productId = this.productId,
        productName = this.productName,
        unitPrice = this.unitPrice,
        quantity = this.quantity,
    )

    companion object {
        fun from(line: OrderLine, order: OrderEntity): OrderLineEntity = OrderLineEntity(
            productId = line.productId,
            productName = line.productName,
            unitPrice = line.unitPrice,
            quantity = line.quantity.value,
            order = order,
        )
    }
}
