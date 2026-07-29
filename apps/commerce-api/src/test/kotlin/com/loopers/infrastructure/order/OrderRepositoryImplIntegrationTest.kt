package com.loopers.infrastructure.order

import com.loopers.domain.order.OrderRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderStatus
import com.loopers.support.error.CoreException
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.hibernate.SessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, OrderRepositoryImpl::class)
class OrderRepositoryImplIntegrationTest @Autowired constructor(
    private val orderRepository: OrderRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun line(productId: Long = 1L, price: Long = 1000L, qty: Int = 1) =
        OrderLine.create(productId = productId, productName = "P-$productId", unitPrice = price, quantity = qty)

    private fun persistPaid(
        userId: Long = 1L,
        idempotencyKey: String = "k-${System.nanoTime()}",
        lines: List<OrderLine> = listOf(line()),
    ): Order {
        val order = Order.create(userId = userId, lines = lines, idempotencyKey = idempotencyKey)
            .also {
                it.markPaymentPending()
                it.markPaid("tx-1", "APPROVED")
            }
        val saved = orderRepository.save(order)
        testEntityManager.flush()
        return saved
    }

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다 (라인 스냅샷·status·운영 메타 포함).")
        @Test
        fun roundTrip() {
            val saved = persistPaid(
                userId = 7L,
                lines = listOf(
                    line(productId = 1L, price = 1000, qty = 2),
                    line(productId = 2L, price = 2000, qty = 3),
                ),
            )
            testEntityManager.clear()

            val found = orderRepository.findById(saved.id)

            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected Order but was null (id=${saved.id})" }
            assertThat(verifiedFound.status).isEqualTo(OrderStatus.PAID)
            assertThat(verifiedFound.paymentTransactionId).isEqualTo("tx-1")
            assertThat(verifiedFound.paymentResultCode).isEqualTo("APPROVED")
            assertThat(verifiedFound.totalAmount).isEqualTo(1000 * 2 + 2000 * 3)
            assertThat(verifiedFound.lines).hasSize(2)
            assertThat(verifiedFound.lines.map { it.productName }).containsExactlyInAnyOrder("P-1", "P-2")
        }

        @DisplayName("쿠폰이 적용된 주문은 userCouponId · discountAmount · 순(net) totalAmount 가 보존된다.")
        @Test
        fun roundTripWithCoupon() {
            val order = Order.create(
                userId = 7L,
                lines = listOf(line(productId = 1L, price = 1000, qty = 3)),
                idempotencyKey = "k-coupon-${System.nanoTime()}",
            ).also { it.applyCoupon(userCouponId = 55L, discountAmount = 700) }
            val saved = orderRepository.save(order)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = requireNotNull(orderRepository.findById(saved.id))
            assertThat(found.userCouponId).isEqualTo(55L)
            assertThat(found.discountAmount).isEqualTo(700)
            assertThat(found.totalAmount).isEqualTo(3000 - 700)
        }

        @DisplayName("DB 에 존재하지 않는 id 로 save(update) 하면, ORDER_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsOrderNotFound_whenUpdatingNonExistentId() {
            val ghost = Order(
                id = 999L,
                userId = 1L,
                lines = listOf(line()),
                orderedAt = LocalDateTime.now(),
                idempotencyKey = "ghost",
            )

            val ex = assertThrows<CoreException> { orderRepository.save(ghost) }

            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }
    }

    @DisplayName("findByUserIdAndIdempotencyKey")
    @Nested
    inner class IdempotencyLookup {
        @DisplayName("정확히 판정한다.")
        @Test
        fun findsByCompositeKey() {
            val saved = persistPaid(userId = 7L, idempotencyKey = "idem-x")
            testEntityManager.clear()

            assertThat(orderRepository.findByUserIdAndIdempotencyKey(7L, "idem-x")?.id).isEqualTo(saved.id)
            assertThat(orderRepository.findByUserIdAndIdempotencyKey(7L, "other")).isNull()
            assertThat(orderRepository.findByUserIdAndIdempotencyKey(99L, "idem-x")).isNull()
        }
    }

    @DisplayName("findAllByUserIdInPeriod")
    @Nested
    inner class MyListing {
        @DisplayName("orderedAt desc 정렬 + 기간 필터 + 본인 userId 필터가 모두 적용된다.")
        @Test
        fun filtersAndOrders() {
            val a = persistPaid(userId = 7L)
            Thread.sleep(10)
            val b = persistPaid(userId = 7L)
            persistPaid(userId = 99L)
            testEntityManager.clear()

            val result = orderRepository.findAllByUserIdInPeriod(
                userId = 7L,
                start = LocalDateTime.now().minusDays(1),
                end = LocalDateTime.now().plusDays(1),
                page = 0,
                size = 10,
            )

            assertThat(result.content.map { it.id }).containsExactly(b.id, a.id)
        }

        @DisplayName("기간을 지정하지 않으면(start·end 모두 null) 본인 주문 전체가 조회된다 — sentinel 시각 매칭 공백 회귀 방지.")
        @Test
        fun returnsAllWhenPeriodUnspecified() {
            val a = persistPaid(userId = 7L)
            Thread.sleep(10)
            val b = persistPaid(userId = 7L)
            persistPaid(userId = 99L)
            testEntityManager.clear()

            val result = orderRepository.findAllByUserIdInPeriod(
                userId = 7L,
                start = null,
                end = null,
                page = 0,
                size = 10,
            )

            assertThat(result.content.map { it.id }).containsExactly(b.id, a.id)
        }

        @DisplayName("start 만 지정하면 하한만, end 만 지정하면 상한만 적용된다 (각 경계 독립적 선택).")
        @Test
        fun appliesPartialBounds() {
            val old = persistPaid(userId = 7L)
            Thread.sleep(10)
            val mid = LocalDateTime.now()
            Thread.sleep(10)
            val recent = persistPaid(userId = 7L)
            testEntityManager.clear()

            val fromMid = orderRepository.findAllByUserIdInPeriod(7L, start = mid, end = null, page = 0, size = 10)
            val untilMid = orderRepository.findAllByUserIdInPeriod(7L, start = null, end = mid, page = 0, size = 10)

            assertThat(fromMid.content.map { it.id }).containsExactly(recent.id)
            assertThat(untilMid.content.map { it.id }).containsExactly(old.id)
        }
    }

    @DisplayName("findAllForAdmin")
    @Nested
    inner class AdminListing {
        @DisplayName("orderedAt desc 정렬 + 페이징이 적용된다 (전 회원 포함).")
        @Test
        fun adminOrdersAndPages() {
            val a = persistPaid(userId = 7L)
            Thread.sleep(10)
            val b = persistPaid(userId = 8L)
            Thread.sleep(10)
            val c = persistPaid(userId = 9L)
            testEntityManager.clear()

            val page0 = orderRepository.findAllForAdmin(0, 2)
            val page1 = orderRepository.findAllForAdmin(1, 2)

            assertThat(page0.content.map { it.id }).containsExactly(c.id, b.id)
            assertThat(page1.content.map { it.id }).containsExactly(a.id)
        }
    }

    @DisplayName("lines 패치 — N+1 방지")
    @Nested
    inner class LinesFetch {
        @DisplayName("주문 20건(각 2라인) 목록 조회 시 lines 가 배치 로딩되어 주문 수에 비례한 쿼리가 발생하지 않는다.")
        @Test
        fun listQueryBatchLoadsLines() {
            val userId = 7L
            repeat(20) { i ->
                persistPaid(
                    userId = userId,
                    idempotencyKey = "idem-$i",
                    lines = listOf(line(productId = 1L), line(productId = 2L)),
                )
            }
            testEntityManager.clear()

            val statistics = testEntityManager.entityManager.entityManagerFactory
                .unwrap(SessionFactory::class.java).statistics
            statistics.isStatisticsEnabled = true
            statistics.clear()

            val orders = orderRepository.findAllByUserIdInPeriod(
                userId = userId,
                start = LocalDateTime.now().minusDays(1),
                end = LocalDateTime.now().plusDays(1),
                page = 0,
                size = 50,
            )

            // toDomain() 이 lines 를 순회하므로 조회 시점에 lines 가 적재된다.
            assertThat(orders.content).hasSize(20)
            assertThat(orders.content.flatMap { it.lines }).hasSize(40)
            // 주문 1회 + lines 배치(IN) 1회 수준. 주문 건수(20)에 비례한 N+1 이면 20+ 가 된다.
            assertThat(statistics.prepareStatementCount).isLessThanOrEqualTo(3)
        }
    }
}
