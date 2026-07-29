package com.loopers.interfaces.api.payment

import com.loopers.application.payment.PaymentFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class PaymentV1Controller(
    private val paymentFacade: PaymentFacade,
) : PaymentV1ApiSpec {
    @PostMapping("/payments")
    @RequireAuth
    override fun pay(
        @LoginUser user: AuthUser,
        @RequestBody request: PaymentV1Dto.PayRequest,
    ): ApiResponse<PaymentV1Dto.PayResponse> {
        val result = paymentFacade.pay(request.toCommand(user.id.toString()))
        return ApiResponse.success(PaymentV1Dto.PayResponse.from(result))
    }

    // 콜백 채널 — 외부 PG 전용(회원 인증 없음). 알 수 없는 거래·중복도 200 으로 수신 확인한다.
    @PostMapping("/payments/callback")
    override fun callback(
        @RequestBody request: PaymentV1Dto.CallbackRequest,
    ): ApiResponse<Any> {
        paymentFacade.settle(request.toTransaction())
        return ApiResponse.success()
    }
}
