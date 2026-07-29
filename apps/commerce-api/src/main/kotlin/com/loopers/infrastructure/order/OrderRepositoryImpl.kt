package com.loopers.infrastructure.order

import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class OrderRepositoryImpl(
    private val orderJpaRepository: OrderJpaRepository,
) : OrderRepository {
    override fun save(order: Order): Order {
        val entity = if (order.id == 0L) {
            OrderEntity.from(order)
        } else {
            orderJpaRepository.findById(order.id)
                .orElseThrow { CoreException(OrderErrorType.ORDER_NOT_FOUND) }
                .apply { syncFrom(order) }
        }
        return orderJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Order? =
        orderJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findByIdForUpdate(id: Long): Order? =
        orderJpaRepository.findByIdForUpdate(id)?.toDomain()

    override fun findByUserIdAndIdempotencyKey(userId: Long, idempotencyKey: String): Order? =
        orderJpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)?.toDomain()

    override fun findAllByUserIdInPeriod(
        userId: Long,
        start: LocalDateTime?,
        end: LocalDateTime?,
        page: Int,
        size: Int,
    ): PageResult<Order> = orderJpaRepository.findAllByUserIdInPeriod(userId, start, end, orderedAtDesc(page, size))
        .toPageResult()

    override fun findAllForAdmin(page: Int, size: Int): PageResult<Order> =
        orderJpaRepository.findAllBy(orderedAtDesc(page, size)).toPageResult()

    private fun orderedAtDesc(page: Int, size: Int): PageRequest =
        // orderedAt 동률 시 페이지 간 중복/누락 방지를 위해 고유 tie-breaker(id)까지 정렬에 고정한다.
        PageRequest.of(page, size, Sort.by(Sort.Order.desc("orderedAt"), Sort.Order.desc("id")))

    private fun Page<OrderEntity>.toPageResult(): PageResult<Order> = PageResult(
        content = content.map { it.toDomain() },
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
}
