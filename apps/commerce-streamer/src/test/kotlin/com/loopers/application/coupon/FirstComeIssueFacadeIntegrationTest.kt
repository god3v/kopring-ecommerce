package com.loopers.application.coupon

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import com.loopers.infrastructure.coupon.CouponEntity
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository
import com.loopers.infrastructure.coupon.CouponIssueRequestRepositoryImpl
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.FirstComeIssuerImpl
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

/**
 * 선착순 처리기(단일 스레드 행위) 검증 — 발급/품절/중복/멱등. 동시성은 별도 통합 테스트에서 다룬다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(
    MySqlTestContainersConfig::class,
    DataSourceConfig::class,
    CouponIssueRequestRepositoryImpl::class,
    FirstComeIssuerImpl::class,
    FirstComeIssueFacade::class,
)
class FirstComeIssueFacadeIntegrationTest @Autowired constructor(
    private val facade: FirstComeIssueFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val requestJpaRepository: CouponIssueRequestJpaRepository,
) {
    private val now: LocalDateTime = LocalDateTime.of(2026, 7, 3, 12, 0, 0)

    private fun seedCoupon(issueLimit: Long?): Long =
        couponJpaRepository.saveAndFlush(
            CouponEntity.create(
                issueStartAt = now.minusDays(1),
                issueEndAt = now.plusDays(30),
                useStartAt = now.minusDays(1),
                useEndAt = now.plusDays(60),
                issueLimit = issueLimit,
            ),
        ).id

    private fun seedRequest(requestId: String, userId: Long, couponId: Long) {
        requestJpaRepository.saveAndFlush(CouponIssueRequestEntity.create(requestId, userId, couponId, now))
    }

    @Test
    fun `접수된 요청을 처리하면 한도 안에서 발급되어 ISSUED 로 확정되고 발급 쿠폰이 생긴다`() {
        val couponId = seedCoupon(issueLimit = 2)
        seedRequest("r1", userId = 1L, couponId = couponId)

        facade.handle("r1")

        val request = requestJpaRepository.findByRequestId("r1")!!
        assertThat(request.status).isEqualTo(IssueRequestStatus.ISSUED)
        assertThat(request.issuedUserCouponId).isNotNull()
        assertThat(userCouponJpaRepository.existsByUserIdAndCouponId(1L, couponId)).isTrue()
        assertThat(couponJpaRepository.findById(couponId).get().issuedCount).isEqualTo(1L)
    }

    @Test
    fun `한도가 소진된 뒤 처리된 요청은 SOLD_OUT 으로 거절된다`() {
        val couponId = seedCoupon(issueLimit = 1)
        seedRequest("r1", userId = 1L, couponId = couponId)
        seedRequest("r2", userId = 2L, couponId = couponId)

        facade.handle("r1")
        facade.handle("r2")

        val second = requestJpaRepository.findByRequestId("r2")!!
        assertThat(second.status).isEqualTo(IssueRequestStatus.REJECTED)
        assertThat(second.rejectReason).isEqualTo(RejectReason.SOLD_OUT)
        assertThat(userCouponJpaRepository.existsByUserIdAndCouponId(2L, couponId)).isFalse()
        assertThat(couponJpaRepository.findById(couponId).get().issuedCount).isEqualTo(1L)
    }

    @Test
    fun `같은 회원의 중복 요청은 ALREADY_ISSUED 로 거절되고 발급 수가 늘지 않는다`() {
        val couponId = seedCoupon(issueLimit = 10)
        seedRequest("r1", userId = 1L, couponId = couponId)
        seedRequest("r2", userId = 1L, couponId = couponId)

        facade.handle("r1")
        facade.handle("r2")

        val second = requestJpaRepository.findByRequestId("r2")!!
        assertThat(second.status).isEqualTo(IssueRequestStatus.REJECTED)
        assertThat(second.rejectReason).isEqualTo(RejectReason.ALREADY_ISSUED)
        assertThat(couponJpaRepository.findById(couponId).get().issuedCount).isEqualTo(1L)
    }

    @Test
    fun `이미 확정된 요청을 다시 처리해도 발급은 한 번만 반영된다`() {
        val couponId = seedCoupon(issueLimit = 10)
        seedRequest("r1", userId = 1L, couponId = couponId)

        facade.handle("r1")
        facade.handle("r1")

        assertThat(couponJpaRepository.findById(couponId).get().issuedCount).isEqualTo(1L)
        assertThat(userCouponJpaRepository.count()).isEqualTo(1L)
    }
}
