package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Order V1 Admin API", description = "어드민 주문 API")
interface OrderV1AdminApiSpec {
    @Operation(
        summary = "주문 목록 조회",
        description = "운영자가 전사 주문을 주문 시각 내림차순으로 페이지 단위 조회합니다. 각 항목에 운영 메타" +
            "(회원 식별자·회원 표시명·결제 트랜잭션 식별자·결제 결과 코드) 가 포함됩니다.",
    )
    fun getOrders(pageable: Pageable): ApiResponse<OrderV1Dto.AdminOrdersResponse>

    @Operation(
        summary = "주문 상세 조회",
        description = "운영자가 단일 주문 상세를 조회합니다. 본인 한정 제약 없이 어느 회원의 주문이든 조회하며, 미존재는 404 입니다.",
    )
    fun getOrder(orderId: Long): ApiResponse<OrderV1Dto.AdminOrderResponse>
}
