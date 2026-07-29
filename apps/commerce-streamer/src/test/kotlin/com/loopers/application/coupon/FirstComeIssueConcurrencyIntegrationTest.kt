package com.loopers.application.coupon

import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.infrastructure.coupon.CouponEntity
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository
import com.loopers.infrastructure.coupon.CouponJpaRepository
import com.loopers.infrastructure.coupon.UserCouponJpaRepository
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 선착순 발급의 동시성 보장 검증 — 대량 동시 요청에도 발급 성공 수가 한도를 넘지 않고, 중복 발급이 없다.
 * 원자 조건부 UPDATE(발급 수 소진) 와 `user_coupons` UNIQUE 로 보장하므로 락 없이 성립한다.
 * Kafka 리스너는 띄우지 않고(브로커 불필요) 처리기(`FirstComeIssueFacade`) 를 여러 스레드에서 직접 호출한다.
 */
@SpringBootTest(properties = ["spring.kafka.listener.auto-startup=false"])
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class FirstComeIssueConcurrencyIntegrationTest @Autowired constructor(
    private val facade: FirstComeIssueFacade,
    private val couponJpaRepository: CouponJpaRepository,
    private val userCouponJpaRepository: UserCouponJpaRepository,
    private val requestJpaRepository: CouponIssueRequestJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `대량 동시 요청에도 발급 수가 한도를 넘지 않고 중복 발급이 없다`() {
        val now = LocalDateTime.of(2026, 7, 3, 12, 0, 0)
        val limit = 100L
        val requesters = 130

        val couponId = couponJpaRepository.saveAndFlush(
            CouponEntity.create(
                issueStartAt = now.minusDays(1),
                issueEndAt = now.plusDays(30),
                useStartAt = now.minusDays(1),
                useEndAt = now.plusDays(60),
                issueLimit = limit,
            ),
        ).id

        val requestIds = (1..requesters).map { userId ->
            val requestId = "req-$userId"
            requestJpaRepository.save(CouponIssueRequestEntity.create(requestId, userId.toLong(), couponId, now))
            requestId
        }
        requestJpaRepository.flush()

        val pool = Executors.newFixedThreadPool(16)
        val ready = CountDownLatch(1)
        val futures = requestIds.map { requestId ->
            pool.submit {
                ready.await()
                facade.handle(requestId)
            }
        }
        ready.countDown()
        futures.forEach { it.get() }
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        val issued = requestJpaRepository.findAll().filter { it.status == IssueRequestStatus.ISSUED }
        assertThat(issued).hasSize(limit.toInt())
        assertThat(couponJpaRepository.findById(couponId).get().issuedCount).isEqualTo(limit)
        // 발급된 쿠폰 수 = 한도, 그리고 회원별 최대 1매(UNIQUE) 이므로 중복 0.
        assertThat(userCouponJpaRepository.count()).isEqualTo(limit)
        assertThat(issued.map { it.userId }.toSet()).hasSize(limit.toInt())
    }
}
