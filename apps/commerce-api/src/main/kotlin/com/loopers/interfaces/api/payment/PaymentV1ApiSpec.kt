package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 API", description = "회원 결제 API")
interface PaymentV1ApiSpec {
    @Operation(
        summary = "결제 요청",
        description = "인증된 회원이 자신의 결제 가능 주문에 카드 정보로 결제를 요청합니다. 외부 PG 에 비동기로 위임하고 접수(처리 중) 를 " +
            "즉시 응답하며, 응답 상태는 항상 `REQUESTED` 입니다. 최종 승인/실패는 콜백·폴링으로 확정됩니다. 타인 주문은 403, 미존재는 404, " +
            "결제 불가 상태는 409 로 구분됩니다. 카드 번호는 어떤 응답에도 노출되지 않습니다.",
    )
    fun pay(user: AuthUser, request: PaymentV1Dto.PayRequest): ApiResponse<PaymentV1Dto.PayResponse>

    @Operation(
        summary = "결제 결과 콜백 수신",
        description = "외부 PG 가 결제 결과를 통지하는 콜백 엔드포인트입니다(회원 인증 없음). `transactionKey` 로 결제를 매칭해 정산하며, " +
            "성공이면 주문 결제완료·실패면 결제실패(재고·쿠폰 보상) 로 확정합니다. 같은 콜백이 중복 도착하거나 알 수 없는 거래여도 항상 " +
            "200 으로 수신 확인하며, 정산은 멱등하게 한 번만 반영됩니다.",
    )
    fun callback(request: PaymentV1Dto.CallbackRequest): ApiResponse<Any>
}
