package com.loopers.domain.order

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.ZoneId

class OrderTest {
    private fun line(productId: Long = 1L, price: Long = 1000L, qty: Int = 1) =
        OrderLine.create(productId = productId, productName = "P-$productId", unitPrice = price, quantity = qty)

    @DisplayName("Order 를 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 인자로 생성하면 userId · lines · idempotencyKey · userCouponId 가 보관된다.")
        @Test
        fun preservesFields() {
            val lines = listOf(line(productId = 1L), line(productId = 2L))

            val order = Order.create(userId = 42L, lines = lines, idempotencyKey = "abc", userCouponId = 77L)

            assertThat(order.userId).isEqualTo(42L)
            assertThat(order.lines).hasSize(2)
            assertThat(order.idempotencyKey).isEqualTo("abc")
            assertThat(order.userCouponId).isEqualTo(77L)
        }

        @DisplayName("lines 가 비어 있으면 EMPTY_LINES 예외가 발생한다.")
        @Test
        fun throwsEmptyLines() {
            val ex = assertThrows<CoreException> {
                Order.create(userId = 42L, lines = emptyList(), idempotencyKey = "abc")
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.EMPTY_LINES)
        }

        @DisplayName("idempotencyKey 가 blank 이면 IDEMPOTENCY_KEY_BLANK 예외가 발생한다.")
        @Test
        fun throwsWhenIdempotencyKeyBlank() {
            val ex = assertThrows<CoreException> {
                Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "")
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.IDEMPOTENCY_KEY_BLANK)
        }

        @DisplayName("totalAmount 는 라인 subtotal 의 합과 같다.")
        @Test
        fun totalAmountEqualsSumOfSubtotals() {
            val lines = listOf(
                OrderLine.create(productId = 1L, productName = "T", unitPrice = 1000, quantity = 2),
                OrderLine.create(productId = 2L, productName = "Cap", unitPrice = 500, quantity = 3),
            )

            val order = Order.create(userId = 42L, lines = lines, idempotencyKey = "abc")

            assertThat(order.totalAmount).isEqualTo(2000 + 1500)
        }

        @DisplayName("orderedAt 이 생성 시점에 박힌다 (Asia/Seoul).")
        @Test
        fun orderedAtIsStampedAtCreation() {
            val seoul = ZoneId.of("Asia/Seoul")
            val before = LocalDateTime.now(seoul)

            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            val after = LocalDateTime.now(seoul)
            assertThat(order.orderedAt).isBetween(before, after)
        }

        @DisplayName("초기 status 는 CREATED 이다 (markPaymentPending/markPaid/markPaymentFailed 호출 전).")
        @Test
        fun initialStatusIsCreated() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")

            assertThat(order.status).isEqualTo(OrderStatus.CREATED)
        }
    }

    @DisplayName("쿠폰을 적용할 때, ")
    @Nested
    inner class ApplyCoupon {
        @DisplayName("applyCoupon 은 userCouponId 와 할인 금액을 바인딩하고 totalAmount 에 반영한다.")
        @Test
        fun bindsCouponAndReflectsDiscount() {
            val order = Order.create(
                userId = 42L,
                lines = listOf(OrderLine.create(1L, "T", 1000, 3)),
                idempotencyKey = "abc",
            )

            order.applyCoupon(userCouponId = 7L, discountAmount = 500)

            assertThat(order.userCouponId).isEqualTo(7L)
            assertThat(order.discountAmount).isEqualTo(500)
            assertThat(order.originalAmount).isEqualTo(3000)
            assertThat(order.totalAmount).isEqualTo(2500)
        }

        @DisplayName("할인 금액이 라인 합계를 초과해도 totalAmount 는 0 미만으로 내려가지 않는다.")
        @Test
        fun totalAmountNeverNegative() {
            val order = Order.create(
                userId = 42L,
                lines = listOf(OrderLine.create(1L, "T", 1000, 1)),
                idempotencyKey = "abc",
            )

            order.applyCoupon(userCouponId = 7L, discountAmount = 999_999)

            assertThat(order.totalAmount).isEqualTo(0)
        }

        @DisplayName("쿠폰 미적용 주문의 discountAmount 는 0 이고 totalAmount 는 라인 합과 같다.")
        @Test
        fun noCouponMeansZeroDiscount() {
            val order = Order.create(
                userId = 42L,
                lines = listOf(OrderLine.create(1L, "T", 1000, 2)),
                idempotencyKey = "abc",
            )

            assertThat(order.discountAmount).isEqualTo(0)
            assertThat(order.totalAmount).isEqualTo(2000)
        }
    }

    @DisplayName("markPaid / markPaymentFailed")
    @Nested
    inner class StateTransition {
        @DisplayName("markPaid 호출 시 status=PAID + transactionId/resultCode 가 박힌다.")
        @Test
        fun markPaidUpdatesState() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()

            order.markPaid(transactionId = "tx-1", resultCode = "APPROVED")

            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            assertThat(order.paymentTransactionId).isEqualTo("tx-1")
            assertThat(order.paymentResultCode).isEqualTo("APPROVED")
        }

        @DisplayName("markPaymentFailed 호출 시 status=PAYMENT_FAILED + 메타가 박힌다 (null 도 허용).")
        @Test
        fun markPaymentFailedUpdatesState() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()

            order.markPaymentFailed(transactionId = null, resultCode = "DECLINED")

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(order.paymentTransactionId).isNull()
            assertThat(order.paymentResultCode).isEqualTo("DECLINED")
        }

        @DisplayName("이미 PAID 인데 markPaid 를 다시 호출하면 멱등하게 통과한다 (중복 콜백).")
        @Test
        fun markPaidIsIdempotent() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaid("tx-1", "APPROVED")

            order.markPaid("tx-2", "APPROVED")

            assertThat(order.status).isEqualTo(OrderStatus.PAID)
            assertThat(order.paymentTransactionId).isEqualTo("tx-1")
        }

        @DisplayName("PAID 인 주문을 markPaymentFailed 로 전이하면 INVALID_PAYMENT_TRANSITION 예외가 발생한다.")
        @Test
        fun cannotTransitPaidToFailed() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaid("tx-1", "APPROVED")

            val ex = assertThrows<CoreException> { order.markPaymentFailed(null, "DECLINED") }

            assertThat(ex.errorType).isEqualTo(OrderErrorType.INVALID_PAYMENT_TRANSITION)
            assertThat(order.status).isEqualTo(OrderStatus.PAID)
        }

        @DisplayName("이미 PAYMENT_FAILED 인데 markPaymentFailed 를 다시 호출하면 멱등하게 통과한다.")
        @Test
        fun markPaymentFailedIsIdempotent() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaymentFailed("tx-1", "DECLINED")

            order.markPaymentFailed("tx-2", "OTHER")

            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(order.paymentResultCode).isEqualTo("DECLINED")
        }

        @DisplayName("PAYMENT_FAILED 인 주문을 markPaid 로 전이하면 INVALID_PAYMENT_TRANSITION 예외가 발생한다.")
        @Test
        fun cannotTransitFailedToPaid() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaymentFailed(null, "DECLINED")

            val ex = assertThrows<CoreException> { order.markPaid("tx-1", "APPROVED") }

            assertThat(ex.errorType).isEqualTo(OrderErrorType.INVALID_PAYMENT_TRANSITION)
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }
    }

    @DisplayName("cancel")
    @Nested
    inner class Cancel {
        @DisplayName("결제 대기(PAYMENT_PENDING) 주문을 취소하면 status=CANCELED 가 된다.")
        @Test
        fun cancelsPendingOrder() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()

            order.cancel()

            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }

        @DisplayName("결제 완료(PAID) 주문을 취소하면 status=CANCELED 가 된다 (환불).")
        @Test
        fun cancelsPaidOrder() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaid("tx-1", "APPROVED")

            order.cancel()

            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }

        @DisplayName("이미 CANCELED 인 주문을 다시 취소하면 멱등하게 통과한다.")
        @Test
        fun cancelIsIdempotent() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.cancel()

            order.cancel()

            assertThat(order.status).isEqualTo(OrderStatus.CANCELED)
        }

        @DisplayName("결제 실패(PAYMENT_FAILED) 주문은 취소할 수 없다 (이미 보상 완료 — INVALID_PAYMENT_TRANSITION).")
        @Test
        fun cannotCancelFailedOrder() {
            val order = Order.create(userId = 42L, lines = listOf(line()), idempotencyKey = "abc")
            order.markPaymentPending()
            order.markPaymentFailed(null, "DECLINED")

            val ex = assertThrows<CoreException> { order.cancel() }

            assertThat(ex.errorType).isEqualTo(OrderErrorType.INVALID_PAYMENT_TRANSITION)
            assertThat(order.status).isEqualTo(OrderStatus.PAYMENT_FAILED)
        }
    }
}
