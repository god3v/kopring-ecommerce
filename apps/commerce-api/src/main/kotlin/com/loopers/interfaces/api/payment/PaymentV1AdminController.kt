package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/payments")
class PaymentV1AdminController(
    private val paymentFacade: PaymentFacade,
) : PaymentV1AdminApiSpec {
    @PostMapping("/{paymentId}/sync")
    override fun sync(
        @PathVariable paymentId: Long,
    ): ApiResponse<PaymentV1Dto.SyncResponse> {
        val result = paymentFacade.sync(paymentId)
        return ApiResponse.success(PaymentV1Dto.SyncResponse.from(result))
    }
}
