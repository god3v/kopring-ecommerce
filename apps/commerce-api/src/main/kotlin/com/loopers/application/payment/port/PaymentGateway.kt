package com.loopers.application.payment.port

/**
 * 외부 결제 게이트웨이 — 비동기 PG 에 결제를 위임하는 outbound port.
 * `request` 는 결과를 즉시 확정하지 않고 **접수(거래 식별자 + 처리 중)** 를 돌려받는다 — 확정은 콜백/폴링이 한다.
 */
interface PaymentGateway {
    fun request(request: PaymentRequestCommand): PaymentResponse

    /** 거래 식별자로 외부 결제 상태를 조회한다 — 콜백 유실·타임아웃 복구(폴링)용. */
    fun getTransaction(userId: String, transactionKey: String): PgTransaction

    /** 주문 식별자로 외부 결제건들을 조회한다 — 타임아웃으로 거래 식별자를 확보 못한 결제 복구용. */
    fun getByOrder(userId: String, orderId: Long): List<PgTransaction>
}

/** 결제 요청 입력 — 회원 식별자(X-USER-ID)·주문·금액·카드·결과 통지 주소. */
data class PaymentRequestCommand(
    val userId: String,
    val orderId: Long,
    val amount: Long,
    val cardType: String,
    val cardNo: String,
    val callbackUrl: String,
)

/** 결제 요청 접수 결과 — 외부 거래 식별자와 처리 상태(즉시 확정이 아니라 접수). */
data class PaymentResponse(
    val transactionKey: String,
    val status: PgTransactionStatus,
)

/** 외부 결제 조회 결과 — 거래 식별자·상태·실패 사유(폴링 정산 입력). */
data class PgTransaction(
    val transactionKey: String,
    val status: PgTransactionStatus,
    val reason: String?,
)

/** 외부 PG 의 처리 상태. PENDING(접수·처리 중) → SUCCESS/FAILED 로 비동기 확정된다. */
enum class PgTransactionStatus { PENDING, SUCCESS, FAILED }
