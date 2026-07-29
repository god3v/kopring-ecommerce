package com.loopers.infrastructure.coupon

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.PercentageDiscountPolicy
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
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
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, CouponRepositoryImpl::class)
class CouponRepositoryImplIntegrationTest @Autowired constructor(
    private val couponRepository: CouponRepository,
    private val testEntityManager: TestEntityManager,
) {
    private val now: LocalDateTime = LocalDateTime.of(2026, 6, 9, 12, 0, 0)

    private fun newCoupon(
        name: String = "신규가입 10% 할인",
        type: DiscountType = DiscountType.RATE,
        value: Long = 10,
        minOrderAmount: Long? = 10000,
    ): Coupon = Coupon.create(
        name = name,
        discountType = type,
        discountValue = value,
        minOrderAmount = minOrderAmount,
        issueStartAt = now.minusDays(1),
        issueEndAt = now.plusDays(30),
        useStartAt = now.minusDays(1),
        useEndAt = now.plusDays(60),
        now = now,
    )

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다.")
        @Test
        fun roundTrip() {
            val saved = couponRepository.save(newCoupon())
            testEntityManager.flush()
            testEntityManager.clear()

            val found = requireNotNull(couponRepository.findById(saved.id))
            assertThat(found.name.value).isEqualTo("신규가입 10% 할인")
            assertThat(found.discountPolicy).isEqualTo(PercentageDiscountPolicy(10))
            assertThat(found.minOrderAmount).isEqualTo(10000)
            assertThat(found.issueEndAt).isEqualTo(now.plusDays(30))
            assertThat(found.useEndAt).isEqualTo(now.plusDays(60))
        }

        @DisplayName("findById 는 soft-deleted Coupon 을 null 로 반환한다.")
        @Test
        fun findByIdExcludesDeleted() {
            val saved = couponRepository.save(newCoupon())
            testEntityManager.flush()
            saved.softDelete(now)
            couponRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(couponRepository.findById(saved.id)).isNull()
        }

        @DisplayName("findByIdIncludingDeleted 는 soft-deleted Coupon 도 반환한다.")
        @Test
        fun includingDeletedReturnsDeleted() {
            val saved = couponRepository.save(newCoupon())
            testEntityManager.flush()
            saved.softDelete(now)
            couponRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(couponRepository.findByIdIncludingDeleted(saved.id)).isNotNull()
        }
    }

    @DisplayName("findAll")
    @Nested
    inner class FindAll {
        @DisplayName("createdAt desc 정렬 + soft-deleted 제외 + 페이징이 적용된다.")
        @Test
        fun ordersAndPaginates() {
            val a = couponRepository.save(newCoupon(name = "A"))
            testEntityManager.flush()
            Thread.sleep(10)
            couponRepository.save(newCoupon(name = "B"))
            testEntityManager.flush()
            Thread.sleep(10)
            couponRepository.save(newCoupon(name = "C"))
            testEntityManager.flush()
            // A 를 삭제 → 결과에서 제외되어야 한다.
            a.softDelete(now)
            couponRepository.save(a)
            testEntityManager.flush()
            testEntityManager.clear()

            val page = couponRepository.findAll(0, 10)

            assertThat(page.content.map { it.name.value }).containsExactly("C", "B")
            assertThat(page.totalElements).isEqualTo(2L)
        }
    }

    @DisplayName("findAllByIdsIncludingDeleted")
    @Nested
    inner class FindAllByIds {
        @DisplayName("삭제 포함 다건을 반환한다. 빈 ids 는 빈 목록이다.")
        @Test
        fun returnsIncludingDeleted() {
            val a = couponRepository.save(newCoupon(name = "A"))
            val b = couponRepository.save(newCoupon(name = "B"))
            testEntityManager.flush()
            a.softDelete(now)
            couponRepository.save(a)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = couponRepository.findAllByIdsIncludingDeleted(listOf(a.id, b.id))
            assertThat(found.map { it.name.value }).containsExactlyInAnyOrder("A", "B")
            assertThat(couponRepository.findAllByIdsIncludingDeleted(emptyList())).isEmpty()
        }
    }
}
