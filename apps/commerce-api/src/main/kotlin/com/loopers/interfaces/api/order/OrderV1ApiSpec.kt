package com.loopers.interfaces.api.order

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

@Tag(name = "Order V1 API", description = "회원 주문 API")
interface OrderV1ApiSpec {
    @Operation(
        summary = "주문 생성",
        description = "인증된 회원이 1개 이상의 라인 아이템과 (선택) 발급 쿠폰으로 주문합니다. 재고 차감·쿠폰 사용·주문 저장이 " +
            "단일 트랜잭션으로 처리되고(하나라도 실패 시 전부 롤백), 커밋 후 결제가 반영됩니다. 멱등 키는 `Idempotency-Key` 헤더로 " +
            "전달하며 같은 회원의 같은 키 재요청은 기존 주문으로 수렴합니다. 생성 응답 상태는 `PAYMENT_PENDING` 입니다.",
    )
    fun placeOrder(
        user: AuthUser,
        idempotencyKey: String?,
        request: OrderV1Dto.PlaceOrderRequest,
    ): ApiResponse<OrderV1Dto.OrderResponse>

    @Operation(
        summary = "내 주문 목록 조회",
        description = "인증된 회원의 주문을 주문 시각 내림차순으로 페이지 단위 조회합니다. `startAt`/`endAt` 으로 기간을 좁힐 수 있으며 " +
            "`startAt > endAt` 이면 400 입니다.",
    )
    fun getMyOrders(
        user: AuthUser,
        startAt: LocalDateTime?,
        endAt: LocalDateTime?,
        pageable: Pageable,
    ): ApiResponse<OrderV1Dto.MyOrdersResponse>

    @Operation(
        summary = "내 주문 상세 조회",
        description = "인증된 회원이 자신의 단일 주문 상세를 조회합니다. 타인 주문은 403, 미존재는 404 로 구분됩니다.",
    )
    fun getMyOrderDetail(user: AuthUser, orderId: Long): ApiResponse<OrderV1Dto.OrderResponse>
}
