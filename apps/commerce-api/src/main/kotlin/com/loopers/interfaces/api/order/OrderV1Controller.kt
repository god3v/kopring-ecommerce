package com.loopers.interfaces.api.order

import com.loopers.application.order.OrderFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import com.loopers.interfaces.api.queue.RequireEntryToken
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1")
class OrderV1Controller(
    private val orderFacade: OrderFacade,
) : OrderV1ApiSpec {
    @PostMapping("/orders")
    @RequireAuth
    @RequireEntryToken
    override fun placeOrder(
        @LoginUser user: AuthUser,
        // 미존재/빈 헤더는 빈 문자열로 넘겨 도메인이 IDEMPOTENCY_KEY_BLANK 로 판정하게 한다.
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val result = orderFacade.placeOrder(request.toCommand(user.loginId, idempotencyKey ?: ""))
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(result))
    }

    @GetMapping("/orders")
    @RequireAuth
    override fun getMyOrders(
        @LoginUser user: AuthUser,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startAt: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endAt: LocalDateTime?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<OrderV1Dto.MyOrdersResponse> {
        val result = orderFacade.getMyOrders(user.loginId, startAt, endAt, pageable.pageNumber, pageable.pageSize)
        return ApiResponse.success(OrderV1Dto.MyOrdersResponse.from(result))
    }

    @GetMapping("/orders/{orderId}")
    @RequireAuth
    override fun getMyOrderDetail(
        @LoginUser user: AuthUser,
        @PathVariable orderId: Long,
    ): ApiResponse<OrderV1Dto.OrderResponse> {
        val result = orderFacade.getMyOrderDetail(user.loginId, orderId)
        return ApiResponse.success(OrderV1Dto.OrderResponse.from(result))
    }
}
