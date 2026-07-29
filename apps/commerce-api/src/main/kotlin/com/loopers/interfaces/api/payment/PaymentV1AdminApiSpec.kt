package com.loopers.interfaces.api.payment

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Payment V1 Admin API", description = "운영자 결제 관리 API")
interface PaymentV1AdminApiSpec {
    @Operation(
        summary = "결제 상태 수동 복구",
        description = "운영자가 처리 중(REQUESTED) 결제의 외부 상태를 거래 식별자로 조회해, 확정(성공/실패) 이면 정산하고 갱신된 상태를 응답합니다. " +
            "외부가 아직 처리 중이거나 거래 식별자 미확보면 상태 변화 없이 `settled=false` 로 응답합니다. 미존재 결제는 404, LDAP 인증 실패는 401.",
    )
    fun sync(paymentId: Long): ApiResponse<PaymentV1Dto.SyncResponse>
}
