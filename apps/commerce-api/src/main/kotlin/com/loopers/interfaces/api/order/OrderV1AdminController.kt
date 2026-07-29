package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/orders")
class OrderV1AdminController(
    private val orderFacade: OrderFacade,
) : OrderV1AdminApiSpec {
    @GetMapping
    override fun getOrders(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<OrderV1Dto.AdminOrdersResponse> {
        val result = orderFacade.getOrdersForAdmin(pageable.pageNumber, pageable.pageSize)
        return ApiResponse.success(OrderV1Dto.AdminOrdersResponse.from(result))
    }

    @GetMapping("/{orderId}")
    override fun getOrder(
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.AdminOrderResponse> {
        val result = orderFacade.getOrderForAdmin(orderId)
        return ApiResponse.success(OrderV1Dto.AdminOrderResponse.from(result))
    }
}
