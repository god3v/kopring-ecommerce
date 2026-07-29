package com.loopers.infrastructure.payment

import com.loopers.application.payment.port.PaymentRequestCommand
import com.loopers.application.payment.port.PaymentResponse
import com.loopers.application.payment.port.PgTransaction

/**
 * pg-simulator HTTP 호출 전송 추상화. 전송 라이브러리를 이 뒤로 감춰 어댑터가 구현에 묶이지 않게 한다.
 * 전송 실패(통신 실패·타임아웃·5xx)는 PaymentGatewayException 으로 변환해 던진다.
 */
interface PaymentApiClient {
    fun requestPayment(command: PaymentRequestCommand): PaymentResponse

    fun getTransaction(userId: String, transactionKey: String): PgTransaction

    fun getByOrder(userId: String, orderId: Long): List<PgTransaction>
}
