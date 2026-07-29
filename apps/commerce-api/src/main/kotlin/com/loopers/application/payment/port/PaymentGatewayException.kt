package com.loopers.application.payment.port

/**
 * 외부 PG 연동 실패(통신·타임아웃·회로 차단)를 표현하는 포트 예외. 인프라 예외가 상위로 누수되지 않게 어댑터가 이 타입으로 감싼다.
 * 일시 장애로 간주돼, 결제 요청 경로는 이를 잡아 REQUESTED 로 두고 폴링으로 복구한다.
 */
class PaymentGatewayException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
