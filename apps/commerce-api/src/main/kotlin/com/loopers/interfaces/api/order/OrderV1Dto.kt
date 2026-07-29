package com.loopers.interfaces.api.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.result.AdminOrderResult
import com.loopers.application.order.result.OrderLineResult
import com.loopers.application.order.result.OrderResult
import com.loopers.domain.order.OrderStatus
import com.loopers.support.page.PageResult
import java.time.LocalDateTime

/**
 * 주문 API DTO. `status` 는 도메인 `OrderStatus` 이름을 그대로 직렬화하고,
 * 금액은 원가(`originalAmount`)·할인(`discountAmount`)·결제(`totalAmount`) 세 가지를 노출한다.
 */
class OrderV1Dto {
    data class PlaceOrderRequest(
        val items: List<OrderLineRequest>,
        val userCouponId: Long? = null,
    ) {
        fun toCommand(loginId: String, idempotencyKey: String): PlaceOrderCommand = PlaceOrderCommand(
            loginId = loginId,
            idempotencyKey = idempotencyKey,
            userCouponId = userCouponId,
            lines = items.map { OrderLineCommand(productId = it.productId, quantity = it.quantity) },
        )
    }

    data class OrderLineRequest(
        val productId: Long,
        val quantity: Int,
    )

    data class OrderLineResponse(
        val productId: Long,
        val productName: String,
        val unitPrice: Long,
        val quantity: Int,
        val subtotal: Long,
    ) {
        companion object {
            fun from(line: OrderLineResult): OrderLineResponse = OrderLineResponse(
                productId = line.productId,
                productName = line.productName,
                unitPrice = line.unitPrice,
                quantity = line.quantity,
                subtotal = line.subtotal,
            )
        }
    }

    data class OrderResponse(
        val orderId: Long,
        val userId: Long,
        val status: OrderStatus,
        val orderedAt: LocalDateTime,
        val originalAmount: Long,
        val discountAmount: Long,
        val totalAmount: Long,
        val userCouponId: Long?,
        val items: List<OrderLineResponse>,
    ) {
        companion object {
            fun from(result: OrderResult): OrderResponse = OrderResponse(
                orderId = result.orderId,
                userId = result.userId,
                status = result.status,
                orderedAt = result.orderedAt,
                originalAmount = result.originalAmount,
                discountAmount = result.discountAmount,
                totalAmount = result.totalAmount,
                userCouponId = result.userCouponId,
                items = result.lines.map { OrderLineResponse.from(it) },
            )
        }
    }

    data class MyOrdersResponse(
        val content: List<OrderResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<OrderResult>): MyOrdersResponse = MyOrdersResponse(
                content = page.content.map { OrderResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    /** 어드민 응답 — 회원 응답 필드 + 운영 메타(회원 표시명·결제 트랜잭션 식별자·결제 결과 코드). */
    data class AdminOrderResponse(
        val orderId: Long,
        val userId: Long,
        val userMaskedName: String,
        val status: OrderStatus,
        val orderedAt: LocalDateTime,
        val originalAmount: Long,
        val discountAmount: Long,
        val totalAmount: Long,
        val userCouponId: Long?,
        val paymentTransactionId: String?,
        val paymentResultCode: String?,
        val items: List<OrderLineResponse>,
    ) {
        companion object {
            fun from(result: AdminOrderResult): AdminOrderResponse = AdminOrderResponse(
                orderId = result.orderId,
                userId = result.userId,
                userMaskedName = result.userMaskedName,
                status = result.status,
                orderedAt = result.orderedAt,
                originalAmount = result.originalAmount,
                discountAmount = result.discountAmount,
                totalAmount = result.totalAmount,
                userCouponId = result.userCouponId,
                paymentTransactionId = result.paymentTransactionId,
                paymentResultCode = result.paymentResultCode,
                items = result.lines.map { OrderLineResponse.from(it) },
            )
        }
    }

    data class AdminOrdersResponse(
        val content: List<AdminOrderResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<AdminOrderResult>): AdminOrdersResponse = AdminOrdersResponse(
                content = page.content.map { AdminOrderResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }
}
