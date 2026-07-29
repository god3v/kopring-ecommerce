package com.loopers.infrastructure.coupon

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, UserCouponRepositoryImpl::class)
class UserCouponRepositoryImplIntegrationTest @Autowired constructor(
    private val userCouponRepository: UserCouponRepository,
    private val testEntityManager: TestEntityManager,
) {
    // 발급 헬퍼 — 사용 가능 구간을 2026 전체로 두어 use(at) 시나리오를 포괄한다.
    private fun issue(userId: Long, couponId: Long): UserCoupon = UserCoupon.issue(
        userId = userId,
        couponId = couponId,
        usableFrom = LocalDateTime.of(2026, 1, 1, 0, 0),
        expiredAt = LocalDateTime.of(2026, 12, 31, 23, 59),
    )

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 status / usedAt 이 보존된다.")
        @Test
        fun roundTrip() {
            val saved = userCouponRepository.save(issue(userId = 1L, couponId = 7L))
            testEntityManager.flush()
            testEntityManager.clear()

            val found = requireNotNull(userCouponRepository.findById(saved.id))
            assertThat(found.userId).isEqualTo(1L)
            assertThat(found.couponId).isEqualTo(7L)
            assertThat(found.status).isEqualTo(UserCouponStatus.AVAILABLE)
            assertThat(found.usedAt).isNull()
        }

        @DisplayName("use 후 저장하면 USED / usedAt 이 반영된다.")
        @Test
        fun persistsUsed() {
            val saved = userCouponRepository.save(issue(userId = 1L, couponId = 7L))
            testEntityManager.flush()
            saved.use(LocalDateTime.of(2026, 6, 10, 9, 0))
            userCouponRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = requireNotNull(userCouponRepository.findById(saved.id))
            assertThat(found.status).isEqualTo(UserCouponStatus.USED)
            assertThat(found.usedAt).isEqualTo(LocalDateTime.of(2026, 6, 10, 9, 0))
        }
    }

    @DisplayName("existsByUserIdAndCouponId / UNIQUE")
    @Nested
    inner class ExistsAndUnique {
        @DisplayName("(회원, 템플릿) 보유 여부를 판정한다.")
        @Test
        fun exists() {
            userCouponRepository.save(issue(userId = 1L, couponId = 7L))
            testEntityManager.flush()

            assertThat(userCouponRepository.existsByUserIdAndCouponId(1L, 7L)).isTrue()
            assertThat(userCouponRepository.existsByUserIdAndCouponId(1L, 8L)).isFalse()
            assertThat(userCouponRepository.existsByUserIdAndCouponId(2L, 7L)).isFalse()
        }

        @DisplayName("(user_id, coupon_id) 동일한 두 번째 save 는 UNIQUE 제약으로 실패한다 (1인 1매).")
        @Test
        fun duplicateFails() {
            userCouponRepository.save(issue(userId = 1L, couponId = 7L))
            testEntityManager.flush()

            assertThatThrownBy {
                userCouponRepository.save(issue(userId = 1L, couponId = 7L))
                testEntityManager.flush()
            }.isInstanceOf(Throwable::class.java)
        }
    }

    @DisplayName("findAllByUserId / findAllByCouponId")
    @Nested
    inner class FindAll {
        @DisplayName("findAllByUserId 가 issued_at desc 정렬 + 페이징 + 타 회원 제외 한다.")
        @Test
        fun byUser() {
            userCouponRepository.save(issue(userId = 1L, couponId = 10L))
            testEntityManager.flush()
            Thread.sleep(10)
            userCouponRepository.save(issue(userId = 1L, couponId = 20L))
            testEntityManager.flush()
            Thread.sleep(10)
            userCouponRepository.save(issue(userId = 1L, couponId = 30L))
            userCouponRepository.save(issue(userId = 2L, couponId = 99L))
            testEntityManager.flush()
            testEntityManager.clear()

            val page = userCouponRepository.findAllByUserId(1L, 0, 10)

            assertThat(page.content.map { it.couponId }).containsExactly(30L, 20L, 10L)
            assertThat(page.totalElements).isEqualTo(3L)
        }

        @DisplayName("findAllByCouponId 가 해당 템플릿 발급분만 issued_at desc 로 반환한다.")
        @Test
        fun byCoupon() {
            userCouponRepository.save(issue(userId = 1L, couponId = 7L))
            testEntityManager.flush()
            Thread.sleep(10)
            userCouponRepository.save(issue(userId = 2L, couponId = 7L))
            userCouponRepository.save(issue(userId = 3L, couponId = 8L))
            testEntityManager.flush()
            testEntityManager.clear()

            val page = userCouponRepository.findAllByCouponId(7L, 0, 10)

            assertThat(page.content.map { it.userId }).containsExactly(2L, 1L)
            assertThat(page.totalElements).isEqualTo(2L)
        }
    }
}
