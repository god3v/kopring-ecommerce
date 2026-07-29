package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class CouponTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    private fun create(
        minOrderAmount: Long? = 10000,
        issueStartAt: LocalDateTime = now.minusDays(1),
        issueEndAt: LocalDateTime = now.plusDays(30),
        useStartAt: LocalDateTime = now,
        useEndAt: LocalDateTime = now.plusDays(60),
        issueLimit: Long? = null,
    ): Coupon = Coupon.create(
        name = "신규가입 10% 할인",
        discountType = DiscountType.RATE,
        discountValue = 10,
        minOrderAmount = minOrderAmount,
        issueStartAt = issueStartAt,
        issueEndAt = issueEndAt,
        useStartAt = useStartAt,
        useEndAt = useEndAt,
        now = now,
        issueLimit = issueLimit,
    )

    @DisplayName("선착순(발급 한도) 을 다룰 때, ")
    @Nested
    inner class FirstCome {
        @DisplayName("무제한 템플릿을 선착순 발급 대상으로 확인하면 거부된다.")
        @Test
        fun ensureFirstComeRejectsUnlimited() {
            val coupon = create(issueLimit = null)

            val exception = assertThrows<CoreException> { coupon.ensureFirstCome() }

            assertThat(exception.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("선착순 템플릿은 선착순 발급 대상 확인을 통과한다.")
        @Test
        fun ensureFirstComePassesLimited() {
            val coupon = create(issueLimit = 100)

            assertThatCode { coupon.ensureFirstCome() }.doesNotThrowAnyException()
        }

        @DisplayName("선착순 템플릿을 즉시 발급 대상으로 확인하면 거부된다.")
        @Test
        fun ensureNotFirstComeRejectsLimited() {
            val coupon = create(issueLimit = 100)

            val exception = assertThrows<CoreException> { coupon.ensureNotFirstCome() }

            assertThat(exception.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("무제한 템플릿은 즉시 발급 대상 확인을 통과한다.")
        @Test
        fun ensureNotFirstComePassesUnlimited() {
            val coupon = create(issueLimit = null)

            assertThatCode { coupon.ensureNotFirstCome() }.doesNotThrowAnyException()
        }

        @DisplayName("발급 수가 한도 미만이면 발급 수를 1 늘린다.")
        @Test
        fun issueIncrementsBelowLimit() {
            val coupon = create(issueLimit = 2)

            coupon.issue()

            assertThat(coupon.issuedCount).isEqualTo(1L)
        }

        @DisplayName("발급 수가 한도에 도달하면 추가 발급은 품절로 거부되고 발급 수는 그대로다.")
        @Test
        fun issueRejectedWhenSoldOut() {
            val coupon = create(issueLimit = 1)
            coupon.issue()

            val exception = assertThrows<CoreException> { coupon.issue() }

            assertThat(exception.errorType).isEqualTo(CouponErrorType.COUPON_SOLD_OUT)
            assertThat(coupon.issuedCount).isEqualTo(1L)
        }
    }

    @DisplayName("Coupon 을 생성할 때, ")
    @Nested
    inner class Create {
        @DisplayName("정상 입력으로 템플릿이 생성된다 (deletedAt 은 null).")
        @Test
        fun createsTemplate() {
            val coupon = create(minOrderAmount = 10000)

            assertThat(coupon.name.value).isEqualTo("신규가입 10% 할인")
            assertThat(coupon.discountPolicy).isEqualTo(PercentageDiscountPolicy(10))
            assertThat(coupon.minOrderAmount).isEqualTo(10000)
            assertThat(coupon.issueStartAt).isEqualTo(now.minusDays(1))
            assertThat(coupon.issueEndAt).isEqualTo(now.plusDays(30))
            assertThat(coupon.useStartAt).isEqualTo(now)
            assertThat(coupon.useEndAt).isEqualTo(now.plusDays(60))
            assertThat(coupon.isDeleted()).isFalse()
        }

        @DisplayName("발급 종료가 발급 시작 이후가 아니면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenIssueWindowInverted() {
            val result = assertThrows<CoreException> {
                create(issueStartAt = now.plusDays(5), issueEndAt = now.plusDays(5))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("사용 종료가 사용 시작 이후가 아니면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenUseWindowInverted() {
            val result = assertThrows<CoreException> {
                create(useStartAt = now.plusDays(10), useEndAt = now.plusDays(1))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("발급 종료가 과거(now 이전) 면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenIssueEndIsPast() {
            val result = assertThrows<CoreException> {
                create(issueStartAt = now.minusDays(10), issueEndAt = now.minusDays(1))
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 음수면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenMinOrderAmountNegative() {
            val result = assertThrows<CoreException> { create(minOrderAmount = -1) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("minOrderAmount 가 null 이면 생성된다.")
        @Test
        fun allowsNullMinOrderAmount() {
            assertThat(create(minOrderAmount = null).minOrderAmount).isNull()
        }

        @DisplayName("issueLimit 가 0 이하면 COUPON_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsWhenIssueLimitNotPositive() {
            assertThat(assertThrows<CoreException> { create(issueLimit = 0) }.errorType)
                .isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
            assertThat(assertThrows<CoreException> { create(issueLimit = -1) }.errorType)
                .isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("issueLimit 가 양수면 선착순 템플릿이 생성된다.")
        @Test
        fun createsFirstComeTemplate() {
            assertThat(create(issueLimit = 100).issueLimit).isEqualTo(100L)
        }

        @DisplayName("issueLimit 가 null 이면 무제한 템플릿으로 생성된다.")
        @Test
        fun allowsNullIssueLimit() {
            assertThat(create(issueLimit = null).issueLimit).isNull()
        }
    }

    @DisplayName("calculateDiscount 를 호출할 때, ")
    @Nested
    inner class CalculateDiscount {
        @DisplayName("주문 합계가 minOrderAmount 이상이면 할인 금액을 반환한다.")
        @Test
        fun returnsDiscountWhenAboveMin() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
            )

            assertThat(coupon.calculateDiscount(20000)).isEqualTo(2000)
        }

        @DisplayName("주문 합계가 minOrderAmount 미만이면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsWhenBelowMin() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.RATE,
                discountValue = 10,
                minOrderAmount = 10000,
            )

            val result = assertThrows<CoreException> { coupon.calculateDiscount(9999) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("minOrderAmount 가 null 이면 어떤 주문 합계든 하한 검사를 통과한다.")
        @Test
        fun passesWhenMinIsNull() {
            val coupon = CouponFixture.coupon(
                discountType = DiscountType.FIXED,
                discountValue = 1000,
                minOrderAmount = null,
            )

            assertThat(coupon.calculateDiscount(1)).isEqualTo(1)
        }
    }

    @DisplayName("ensureIssuable 은 ")
    @Nested
    inner class EnsureIssuable {
        @DisplayName("발급 가능 구간 안(경계 포함) 이면 예외 없이 통과한다.")
        @Test
        fun passesWithinIssueWindow() {
            val coupon = CouponFixture.coupon(issueStartAt = now.minusDays(1), issueEndAt = now.plusDays(1))

            assertThatCode { coupon.ensureIssuable(now) }.doesNotThrowAnyException()
            assertThatCode { coupon.ensureIssuable(now.minusDays(1)) }.doesNotThrowAnyException()
            assertThatCode { coupon.ensureIssuable(now.plusDays(1)) }.doesNotThrowAnyException()
        }

        @DisplayName("발급 시작 전이면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsBeforeIssueStart() {
            val coupon = CouponFixture.coupon(issueStartAt = now.plusDays(1), issueEndAt = now.plusDays(10))

            val result = assertThrows<CoreException> { coupon.ensureIssuable(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("발급 종료 후면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsAfterIssueEnd() {
            val coupon = CouponFixture.coupon(issueStartAt = now.minusDays(10), issueEndAt = now.minusSeconds(1))

            val result = assertThrows<CoreException> { coupon.ensureIssuable(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }
    }

    @DisplayName("Coupon 을 수정·삭제할 때, ")
    @Nested
    inner class UpdateAndDelete {
        @DisplayName("update 로 필드가 갱신된다.")
        @Test
        fun updatesFields() {
            val coupon = CouponFixture.coupon(name = "기존", discountType = DiscountType.RATE, discountValue = 10)

            coupon.update(
                name = "변경",
                discountType = DiscountType.FIXED,
                discountValue = 3000,
                minOrderAmount = 5000,
                issueStartAt = now,
                issueEndAt = now.plusDays(10),
                useStartAt = now,
                useEndAt = now.plusDays(20),
                now = now,
            )

            assertThat(coupon.name.value).isEqualTo("변경")
            assertThat(coupon.discountPolicy).isEqualTo(FixedAmountDiscountPolicy(3000))
            assertThat(coupon.minOrderAmount).isEqualTo(5000)
            assertThat(coupon.useEndAt).isEqualTo(now.plusDays(20))
        }

        @DisplayName("update 도 발급 종료 과거를 거부한다.")
        @Test
        fun updateRejectsPastIssueEnd() {
            val coupon = CouponFixture.coupon()

            val result = assertThrows<CoreException> {
                coupon.update(
                    name = "변경",
                    discountType = DiscountType.FIXED,
                    discountValue = 3000,
                    minOrderAmount = null,
                    issueStartAt = now.minusDays(10),
                    issueEndAt = now.minusDays(1),
                    useStartAt = now,
                    useEndAt = now.plusDays(20),
                    now = now,
                )
            }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
        }

        @DisplayName("softDelete(now) 호출 시 deletedAt 이 주입된 시각으로 설정되고 isDeleted() 가 true 다.")
        @Test
        fun softDeletes() {
            val coupon = CouponFixture.coupon()
            val deletedAt = now.plusDays(3)

            coupon.softDelete(deletedAt)

            assertThat(coupon.deletedAt).isEqualTo(deletedAt)
            assertThat(coupon.isDeleted()).isTrue()
        }

        @DisplayName("이미 삭제된 Coupon 의 softDelete() 재호출은 멱등이다 — deletedAt 이 변하지 않는다.")
        @Test
        fun softDeleteIsIdempotent() {
            val coupon = CouponFixture.coupon()
            val firstDeletedAt = now.plusDays(1)
            coupon.softDelete(firstDeletedAt)

            coupon.softDelete(now.plusDays(2))

            assertThat(coupon.deletedAt).isEqualTo(firstDeletedAt)
        }
    }
}
