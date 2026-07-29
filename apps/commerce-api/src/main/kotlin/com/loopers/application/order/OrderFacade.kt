package com.loopers.application.order

import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.order.result.AdminOrderResult
import com.loopers.application.order.result.OrderResult
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.order.OrderService
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class OrderFacade(
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val orderRepository: OrderRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun placeOrder(command: PlaceOrderCommand): OrderResult {
        val user = userRepository.findByLoginId(command.loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)

        orderRepository.findByUserIdAndIdempotencyKey(user.id, command.idempotencyKey)?.let {
            return OrderResult.from(it)
        }

        val productIds = command.lines.map { it.productId }.distinct()
        val products = productRepository.findAllByIdsForUpdate(productIds).associateBy { it.id }
        val quantities = command.lines.associate { it.productId to it.quantity }
        val order = OrderService.createOrder(
            userId = user.id,
            products = products,
            quantities = quantities,
            idempotencyKey = command.idempotencyKey,
        )

        command.userCouponId?.let { userCouponId ->
            val userCoupon = userCouponRepository.findByIdForUpdate(userCouponId)
                ?: throw CoreException(CouponErrorType.USER_COUPON_NOT_FOUND)
            userCoupon.ensureOwnedBy(user.id)
            val coupon = couponRepository.findByIdIncludingDeleted(userCoupon.couponId)
                ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
            val now = LocalDateTime.now()
            val discount = coupon.calculateDiscount(order.originalAmount)
            userCoupon.use(now)
            userCouponRepository.save(userCoupon)
            order.applyCoupon(userCouponId, discount)
        }

        productRepository.saveAll(products.values)
        val saved = orderRepository.save(order)

        eventPublisher.publish(OrderEvent.Created.from(saved))
        return OrderResult.from(saved)
    }

    @Transactional(readOnly = true)
    fun getMyOrders(
        loginId: String,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        page: Int,
        size: Int,
    ): PageResult<OrderResult> {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (startAt != null && endAt != null && startAt.isAfter(endAt)) {
            throw CoreException(OrderErrorType.INVALID_DATE_RANGE)
        }
        return orderRepository
            .findAllByUserIdInPeriod(user.id, startAt, endAt, page, size)
            .map { OrderResult.from(it) }
    }

    @Transactional(readOnly = true)
    fun getMyOrderDetail(loginId: String, orderId: Long): OrderResult {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        if (order.userId != user.id) {
            throw CoreException(OrderErrorType.ORDER_FORBIDDEN)
        }
        return OrderResult.from(order)
    }

    @Transactional(readOnly = true)
    fun getOrdersForAdmin(page: Int, size: Int): PageResult<AdminOrderResult> {
        val orders = orderRepository.findAllForAdmin(page, size)
        val usersById = userRepository.findAllByIds(orders.content.map { it.userId }).associateBy { it.id }
        return orders.map { order ->
            val user = usersById[order.userId]
                ?: throw CoreException(UserErrorType.UNAUTHORIZED)
            AdminOrderResult.of(order, user)
        }
    }

    @Transactional(readOnly = true)
    fun getOrderForAdmin(orderId: Long): AdminOrderResult {
        val order = orderRepository.findById(orderId)
            ?: throw CoreException(OrderErrorType.ORDER_NOT_FOUND)
        val user = userRepository.findById(order.userId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        return AdminOrderResult.of(order, user)
    }
}
