package com.loopers.domain.coupon

import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class UserCouponTest {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    @DisplayName("발급할 때, ")
    @Nested
    inner class Issue {
        @DisplayName("issue() 로 사용 가능 구간을 스냅샷한 AVAILABLE 발급 쿠폰이 만들어진다.")
        @Test
        fun issuesAvailable() {
            val userCoupon = UserCoupon.issue(
                userId = 1L,
                couponId = 7L,
                usableFrom = now,
                expiredAt = now.plusDays(30),
            )

            assertThat(userCoupon.userId).isEqualTo(1L)
            assertThat(userCoupon.couponId).isEqualTo(7L)
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
            assertThat(userCoupon.usableFrom).isEqualTo(now)
            assertThat(userCoupon.expiredAt).isEqualTo(now.plusDays(30))
            assertThat(userCoupon.usedAt).isNull()
        }
    }

    @DisplayName("소유자를 검증할 때, ")
    @Nested
    inner class EnsureOwnedBy {
        @DisplayName("소유자 본인이면 통과한다.")
        @Test
        fun passesForOwner() {
            val userCoupon = CouponFixture.userCoupon(userId = 1L)

            userCoupon.ensureOwnedBy(1L)
        }

        @DisplayName("타인의 쿠폰이면 존재를 숨기기 위해 USER_COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsNotFoundForOthers() {
            val userCoupon = CouponFixture.userCoupon(userId = 1L)

            val result = assertThrows<CoreException> { userCoupon.ensureOwnedBy(2L) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.USER_COUPON_NOT_FOUND)
        }
    }

    @DisplayName("사용할 때, ")
    @Nested
    inner class Use {
        @DisplayName("사용 가능 구간 안에서 use(at) 호출 시 USED 로 전이되고 usedAt 이 채워진다.")
        @Test
        fun transitionsToUsed() {
            val userCoupon = CouponFixture.userCoupon(
                status = UserCouponStatus.AVAILABLE,
                usableFrom = now.minusDays(1),
                expiredAt = now.plusDays(1),
            )

            userCoupon.use(now)

            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
            assertThat(userCoupon.usedAt).isEqualTo(now)
        }

        @DisplayName("이미 USED 인 쿠폰의 use(at) 는 ALREADY_USED_COUPON 예외가 발생한다.")
        @Test
        fun throwsWhenAlreadyUsed() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.USED, usedAt = now.minusDays(1))

            val result = assertThrows<CoreException> { userCoupon.use(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.ALREADY_USED_COUPON)
        }

        @DisplayName("사용 시작 전이면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsBeforeUsableFrom() {
            val userCoupon = CouponFixture.userCoupon(usableFrom = now.plusDays(1), expiredAt = now.plusDays(10))

            val result = assertThrows<CoreException> { userCoupon.use(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }

        @DisplayName("만료 시각 이후면 COUPON_NOT_APPLICABLE 예외가 발생한다.")
        @Test
        fun throwsAfterExpired() {
            val userCoupon = CouponFixture.userCoupon(usableFrom = now.minusDays(10), expiredAt = now.minusSeconds(1))

            val result = assertThrows<CoreException> { userCoupon.use(now) }
            assertThat(result.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        }
    }

    @DisplayName("사용을 취소할 때, ")
    @Nested
    inner class CancelUse {
        @DisplayName("USED 쿠폰의 cancelUse() 는 AVAILABLE 로 되돌리고 usedAt 을 비운다.")
        @Test
        fun revertsUsedToAvailable() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.USED, usedAt = now)

            userCoupon.cancelUse()

            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
            assertThat(userCoupon.usedAt).isNull()
        }

        @DisplayName("AVAILABLE 쿠폰의 cancelUse() 는 멱등 no-op 이다.")
        @Test
        fun idempotentWhenNotUsed() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE)

            userCoupon.cancelUse()

            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.AVAILABLE)
            assertThat(userCoupon.usedAt).isNull()
        }
    }

    @DisplayName("노출 상태를 파생할 때, ")
    @Nested
    inner class ViewStatus {
        @DisplayName("USED 면 만료 시각을 지나도 USED 로 노출된다 (사용 우선).")
        @Test
        fun usedTakesPrecedence() {
            val userCoupon = CouponFixture.userCoupon(
                status = UserCouponStatus.USED,
                usedAt = now.minusDays(2),
                expiredAt = now.minusDays(1),
            )

            assertThat(userCoupon.viewStatus(now)).isEqualTo(UserCouponStatus.USED)
        }

        @DisplayName("AVAILABLE + 만료 경과면 EXPIRED 로 노출된다.")
        @Test
        fun derivesExpired() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE, expiredAt = now.minusSeconds(1))

            assertThat(userCoupon.viewStatus(now)).isEqualTo(UserCouponStatus.EXPIRED)
        }

        @DisplayName("AVAILABLE + 만료 전이면 AVAILABLE 로 노출된다.")
        @Test
        fun staysAvailable() {
            val userCoupon = CouponFixture.userCoupon(status = UserCouponStatus.AVAILABLE, expiredAt = now.plusDays(1))

            assertThat(userCoupon.viewStatus(now)).isEqualTo(UserCouponStatus.AVAILABLE)
        }
    }
}
