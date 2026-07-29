package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.order.OrderEvent
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserRepository
import com.loopers.domain.order.Order
import com.loopers.domain.order.OrderErrorType
import com.loopers.domain.order.OrderLine
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("OrderFacade")
class OrderFacadeTest {
    private val userRepository: UserRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val orderRepository: OrderRepository = mockk()
    private val couponRepository: CouponRepository = mockk()
    private val userCouponRepository: UserCouponRepository = mockk()
    private val eventPublisher: DomainEventPublisher = mockk(relaxed = true)
    private val orderFacade =
        OrderFacade(userRepository, productRepository, orderRepository, couponRepository, userCouponRepository, eventPublisher)

    private val loginId = UserFixture.DEFAULT_LOGIN_ID
    private val idempotencyKey = "idem-001"

    private fun placeOrderCommand(
        userCouponId: Long? = null,
        lines: List<OrderLineCommand> = listOf(OrderLineCommand(productId = 1L, quantity = 1)),
    ) = PlaceOrderCommand(
        loginId = loginId,
        idempotencyKey = idempotencyKey,
        userCouponId = userCouponId,
        lines = lines,
    )

    @Nested
    @DisplayName("placeOrder — UC-1 정상 흐름")
    inner class PlaceOrderHappy {
        @Test
        @DisplayName("각 상품의 deductStock 이 호출되고 Order 가 저장된다")
        fun savesOrder() {
            val user = UserFixture.validUser()
            val productA = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            val productB = ProductFixture.validProduct(id = 2L, name = "B", price = 2000, stock = 5)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    lines = listOf(
                        OrderLineCommand(productId = 1L, quantity = 2),
                        OrderLineCommand(productId = 2L, quantity = 3),
                    ),
                ),
            )

            assertThat(productA.stock.value).isEqualTo(8)
            assertThat(productB.stock.value).isEqualTo(2)
            verify(exactly = 1) { productRepository.findAllByIdsForUpdate(any()) }
            verify(exactly = 1) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("주문이 저장되면 OrderEvent.Created 를 발행한다(userId·총액·라인 스냅샷 포함)")
        fun publishesOrderCreatedEvent() {
            val user = UserFixture.validUser()
            val productA = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            val productB = ProductFixture.validProduct(id = 2L, name = "B", price = 2000, stock = 5)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    lines = listOf(
                        OrderLineCommand(productId = 1L, quantity = 2),
                        OrderLineCommand(productId = 2L, quantity = 3),
                    ),
                ),
            )

            verify {
                eventPublisher.publish(
                    match {
                        it is OrderEvent.Created &&
                            it.userId == user.id &&
                            it.totalAmount == 8000L &&
                            it.lines.map { line -> line.productId to line.quantity }
                                .toSet() == setOf(1L to 2, 2L to 3)
                    },
                )
            }
        }

        @Test
        @DisplayName("저장된 주문의 라인 스냅샷에는 호출 시점 상품의 name · price 가 박혀 있다")
        fun linesCarrySnapshot() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "맥북", price = 1_000_000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(lines = listOf(OrderLineCommand(productId = 1L, quantity = 2))),
            )

            assertThat(savedOrder.captured.lines[0].productName).isEqualTo("맥북")
            assertThat(savedOrder.captured.lines[0].unitPrice).isEqualTo(1_000_000)
        }

        @Test
        @DisplayName("totalAmount 는 unitPrice × quantity 합과 같다")
        fun totalAmountIsSumOfSubtotals() {
            val user = UserFixture.validUser()
            val productA = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            val productB = ProductFixture.validProduct(id = 2L, name = "B", price = 2000, stock = 5)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    lines = listOf(
                        OrderLineCommand(productId = 1L, quantity = 2),
                        OrderLineCommand(productId = 2L, quantity = 3),
                    ),
                ),
            )

            assertThat(savedOrder.captured.totalAmount).isEqualTo(1000 * 2 + 2000 * 3)
        }

        @Test
        @DisplayName("userCouponId 가 있으면 쿠폰이 소진되고 할인 금액이 합계에 반영된다")
        fun appliesCouponDiscount() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            // 원 합계 2000 에 대해 정액 500 할인 쿠폰을 보유한 상황을 모사한다.
            val userCoupon = CouponFixture.userCoupon(id = 99L, userId = user.id, couponId = 7L)
            every { userCouponRepository.findByIdForUpdate(99L) } returns userCoupon
            every { couponRepository.findByIdIncludingDeleted(7L) } returns
                CouponFixture.coupon(id = 7L, discountType = DiscountType.FIXED, discountValue = 500)
            every { userCouponRepository.save(any()) } returns userCoupon
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(
                    userCouponId = 99L,
                    lines = listOf(OrderLineCommand(productId = 1L, quantity = 2)),
                ),
            )

            assertThat(savedOrder.captured.userCouponId).isEqualTo(99L)
            assertThat(savedOrder.captured.discountAmount).isEqualTo(500L)
            assertThat(savedOrder.captured.totalAmount).isEqualTo(1500L)
            assertThat(userCoupon.status).isEqualTo(UserCouponStatus.USED)
            verify(exactly = 1) { userCouponRepository.save(userCoupon) }
        }

        @Test
        @DisplayName("userCouponId 가 없으면 쿠폰 적용을 시도하지 않고 합계는 라인 합과 같다")
        fun noCouponNoDiscount() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }
            val savedOrder = slot<Order>()
            every { orderRepository.save(capture(savedOrder)) } answers { savedOrder.captured }

            orderFacade.placeOrder(
                placeOrderCommand(lines = listOf(OrderLineCommand(productId = 1L, quantity = 2))),
            )

            assertThat(savedOrder.captured.discountAmount).isEqualTo(0L)
            assertThat(savedOrder.captured.totalAmount).isEqualTo(2000L)
            verify(exactly = 0) { userCouponRepository.findByIdForUpdate(any()) }
        }
    }

    @Nested
    @DisplayName("placeOrder — 쿠폰 적용 실패 시 주문·재고가 저장되지 않는다")
    inner class PlaceOrderCouponFailure {
        private fun arrangeUser(): com.loopers.domain.user.User {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)
            return user
        }

        private fun assertOrderFails(expected: CouponErrorType) {
            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    placeOrderCommand(userCouponId = 99L, lines = listOf(OrderLineCommand(productId = 1L, quantity = 2))),
                )
            }
            assertThat(ex.errorType).isEqualTo(expected)
            verify(exactly = 0) { orderRepository.save(any()) }
            verify(exactly = 0) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("발급 쿠폰이 없으면 USER_COUPON_NOT_FOUND")
        fun userCouponNotFound() {
            arrangeUser()
            every { userCouponRepository.findByIdForUpdate(99L) } returns null
            assertOrderFails(CouponErrorType.USER_COUPON_NOT_FOUND)
        }

        @Test
        @DisplayName("타 유저 소유 쿠폰이면 USER_COUPON_NOT_FOUND")
        fun othersCoupon() {
            val user = arrangeUser()
            every { userCouponRepository.findByIdForUpdate(99L) } returns
                CouponFixture.userCoupon(id = 99L, userId = user.id + 1L, couponId = 7L)
            assertOrderFails(CouponErrorType.USER_COUPON_NOT_FOUND)
        }

        @Test
        @DisplayName("이미 사용된 쿠폰이면 ALREADY_USED_COUPON")
        fun alreadyUsed() {
            val user = arrangeUser()
            every { userCouponRepository.findByIdForUpdate(99L) } returns
                CouponFixture.userCoupon(id = 99L, userId = user.id, couponId = 7L, status = UserCouponStatus.USED)
            every { couponRepository.findByIdIncludingDeleted(7L) } returns CouponFixture.coupon(id = 7L)
            assertOrderFails(CouponErrorType.ALREADY_USED_COUPON)
        }

        @Test
        @DisplayName("사용 기간이 지난 쿠폰이면 COUPON_NOT_APPLICABLE (발급 쿠폰의 expiredAt 으로 판정)")
        fun expiredCoupon() {
            val user = arrangeUser()
            every { userCouponRepository.findByIdForUpdate(99L) } returns
                CouponFixture.userCoupon(
                    id = 99L,
                    userId = user.id,
                    couponId = 7L,
                    expiredAt = LocalDateTime.now().minusDays(1),
                )
            every { couponRepository.findByIdIncludingDeleted(7L) } returns CouponFixture.coupon(id = 7L)
            assertOrderFails(CouponErrorType.COUPON_NOT_APPLICABLE)
        }
    }

    @Nested
    @DisplayName("placeOrder — UC-1 멱등 수렴")
    inner class Idempotency {
        @Test
        @DisplayName("같은 (userId, idempotencyKey) 재호출 시 신규 저장 없이 기존 OrderResult 가 반환된다")
        fun returnsExistingOrder() {
            val user = UserFixture.validUser()
            val existing = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "X", 100, 1)),
                idempotencyKey = idempotencyKey,
            ).also {
                it.markPaymentPending()
                it.markPaid("prev-tx", "APPROVED")
            }
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns existing

            val result = orderFacade.placeOrder(placeOrderCommand())

            assertThat(result.status).isEqualTo(OrderStatus.PAID)
            verify(exactly = 0) { productRepository.findById(any()) }
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        @DisplayName("쿠폰을 적용했던 주문을 같은 멱등 키로 재요청하면 쿠폰을 다시 소진하지 않는다")
        fun doesNotReconsumeCouponOnIdempotentHit() {
            val user = UserFixture.validUser()
            val existing = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "X", 1000, 1)),
                idempotencyKey = idempotencyKey,
            ).also { it.applyCoupon(userCouponId = 5L, discountAmount = 100) }
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns existing

            val result = orderFacade.placeOrder(placeOrderCommand(userCouponId = 5L))

            assertThat(result.userCouponId).isEqualTo(5L)
            assertThat(result.discountAmount).isEqualTo(100L)
            verify(exactly = 0) { userCouponRepository.findByIdForUpdate(any()) }
            verify(exactly = 0) { orderRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("placeOrder — UC-1 예외 흐름")
    inner class PlaceOrderExceptions {
        @Test
        @DisplayName("loginId 회원 없음 → UNAUTHORIZED")
        fun unauthorizedWhenUserMissing() {
            every { userRepository.findByLoginId(loginId) } returns null

            val ex = assertThrows<CoreException> { orderFacade.placeOrder(placeOrderCommand()) }
            assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
            verify(exactly = 0) { orderRepository.save(any()) }
        }

        @Test
        @DisplayName("라인 중 상품 없음 → PRODUCT_NOT_FOUND, orderRepository.save 미호출")
        fun productNotFound() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns emptyList()

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    placeOrderCommand(lines = listOf(OrderLineCommand(productId = 99L, quantity = 1))),
                )
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { orderRepository.save(any()) }
            verify(exactly = 0) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("재고 부족 → INSUFFICIENT_STOCK, orderRepository.save 미호출")
        fun insufficientStock() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 1)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, idempotencyKey) } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    placeOrderCommand(lines = listOf(OrderLineCommand(productId = 1L, quantity = 5))),
                )
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.INSUFFICIENT_STOCK)
            verify(exactly = 0) { orderRepository.save(any()) }
            verify(exactly = 0) { productRepository.saveAll(any()) }
        }

        @Test
        @DisplayName("idempotencyKey 가 blank 면 IDEMPOTENCY_KEY_BLANK 예외 (도메인 위임)")
        fun idempotencyKeyBlank() {
            val user = UserFixture.validUser()
            val product = ProductFixture.validProduct(id = 1L, name = "A", price = 1000, stock = 10)
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findByUserIdAndIdempotencyKey(user.id, "") } returns null
            every { productRepository.findAllByIdsForUpdate(any()) } returns listOf(product)
            every { productRepository.saveAll(any()) } answers { firstArg<Collection<com.loopers.domain.product.Product>>().toList() }

            val ex = assertThrows<CoreException> {
                orderFacade.placeOrder(
                    PlaceOrderCommand(
                        loginId = loginId,
                        idempotencyKey = "",
                        userCouponId = null,
                        lines = listOf(OrderLineCommand(productId = 1L, quantity = 1)),
                    ),
                )
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.IDEMPOTENCY_KEY_BLANK)
        }
    }

    @Nested
    @DisplayName("getMyOrders — UC-2")
    inner class GetMyOrders {
        @Test
        @DisplayName("기간 + 페이징 + 본인 userId 가 Repository 에 전달된다")
        fun delegatesFiltering() {
            val user = UserFixture.validUser()
            val start = LocalDateTime.of(2026, 5, 1, 0, 0)
            val end = LocalDateTime.of(2026, 5, 28, 23, 59)
            every { userRepository.findByLoginId(loginId) } returns user
            every {
                orderRepository.findAllByUserIdInPeriod(user.id, start, end, 1, 30)
            } returns PageResult(emptyList(), 1, 30, 0, 0)

            orderFacade.getMyOrders(loginId, start, end, page = 1, size = 30)

            verify { orderRepository.findAllByUserIdInPeriod(user.id, start, end, 1, 30) }
        }

        @Test
        @DisplayName("기간 미지정(null) 이면 sentinel 시각이 아니라 null 경계가 그대로 Repository 에 전달된다")
        fun passesNullPeriodThrough() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user
            every {
                orderRepository.findAllByUserIdInPeriod(user.id, null, null, 0, 20)
            } returns PageResult(emptyList(), 0, 20, 0, 0)

            orderFacade.getMyOrders(loginId, startAt = null, endAt = null, page = 0, size = 20)

            // LocalDateTime.MIN/MAX 로 치환하지 않는다 — 그 값은 MySQL DATETIME 범위 밖이라 매칭이 빈다.
            verify { orderRepository.findAllByUserIdInPeriod(user.id, null, null, 0, 20) }
        }

        @Test
        @DisplayName("loginId 회원 없음 → UNAUTHORIZED")
        fun unauthorized() {
            every { userRepository.findByLoginId(loginId) } returns null

            val ex = assertThrows<CoreException> {
                orderFacade.getMyOrders(loginId, null, null, 0, 20)
            }
            assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }

        @Test
        @DisplayName("startAt 이 endAt 보다 크면 INVALID_DATE_RANGE")
        fun invalidDateRange() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user

            val ex = assertThrows<CoreException> {
                orderFacade.getMyOrders(
                    loginId,
                    LocalDateTime.of(2026, 5, 10, 0, 0),
                    LocalDateTime.of(2026, 5, 1, 0, 0),
                    0,
                    20,
                )
            }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.INVALID_DATE_RANGE)
        }
    }

    @Nested
    @DisplayName("getMyOrderDetail — UC-3")
    inner class GetMyOrderDetail {
        @Test
        @DisplayName("본인 주문이면 OrderResult 가 반환된다")
        fun returnsOwnOrder() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also {
                it.markPaymentPending()
                it.markPaid("tx", "OK")
            }
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(10L) } returns order

            val result = orderFacade.getMyOrderDetail(loginId, 10L)

            assertThat(result.status).isEqualTo(OrderStatus.PAID)
        }

        @Test
        @DisplayName("존재하지 않으면 ORDER_NOT_FOUND")
        fun notFound() {
            val user = UserFixture.validUser()
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { orderFacade.getMyOrderDetail(loginId, 99L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }

        @Test
        @DisplayName("타인 주문이면 ORDER_FORBIDDEN")
        fun forbidden() {
            val user = UserFixture.validUser()
            val foreignOrder = Order.create(
                userId = 99L,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            )
            every { userRepository.findByLoginId(loginId) } returns user
            every { orderRepository.findById(10L) } returns foreignOrder

            val ex = assertThrows<CoreException> { orderFacade.getMyOrderDetail(loginId, 10L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_FORBIDDEN)
        }
    }

    @Nested
    @DisplayName("getOrdersForAdmin — UC-4")
    inner class GetOrdersForAdmin {
        @Test
        @DisplayName("page/size 가 Repository 에 전달되고 결과는 운영 메타가 포함된 AdminOrderResult")
        fun delegatesAndIncludesAdminMeta() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also {
                it.markPaymentPending()
                it.markPaid("tx-9", "APPROVED")
            }
            every { orderRepository.findAllForAdmin(0, 20) } returns PageResult(listOf(order), 0, 20, 1, 1)
            every { userRepository.findAllByIds(any()) } returns listOf(user)

            val result = orderFacade.getOrdersForAdmin(0, 20)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].userMaskedName).endsWith("*")
            assertThat(result.content[0].paymentTransactionId).isEqualTo("tx-9")
            assertThat(result.content[0].paymentResultCode).isEqualTo("APPROVED")
        }

        @Test
        @DisplayName("결제실패 + PG 트랜잭션 누락이면 빈 값으로 응답된다")
        fun emptyMetaWhenPaymentFailedWithoutTransaction() {
            val user = UserFixture.validUser()
            val failed = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also {
                it.markPaymentPending()
                it.markPaymentFailed(null, null)
            }
            every { orderRepository.findAllForAdmin(0, 20) } returns PageResult(listOf(failed), 0, 20, 1, 1)
            every { userRepository.findAllByIds(any()) } returns listOf(user)

            val result = orderFacade.getOrdersForAdmin(0, 20)

            assertThat(result.content[0].status).isEqualTo(OrderStatus.PAYMENT_FAILED)
            assertThat(result.content[0].paymentTransactionId).isNull()
            assertThat(result.content[0].paymentResultCode).isNull()
        }

        @Test
        @DisplayName("페이지 50건이어도 회원 조회는 findAllByIds 1회로 끝나고 findById N+1 이 없다")
        fun batchLoadsUsersForPageSize50() = assertUserLookupIsConstant(size = 50)

        @Test
        @DisplayName("페이지 100건이어도 회원 조회는 findAllByIds 1회로 끝나고 findById N+1 이 없다")
        fun batchLoadsUsersForPageSize100() = assertUserLookupIsConstant(size = 100)

        /**
         * 페이지 크기와 무관하게 회원 조회가 상수(1회 일괄 조회)로 유지되는지 검증한다.
         * 회귀 방지: 주문 N 건마다 findById 를 호출하던 N+1 패턴으로 되돌아가면 실패한다.
         */
        private fun assertUserLookupIsConstant(size: Int) {
            val ids = (1L..size.toLong())
            val users = ids.map { UserFixture.validUser(loginId = "user$it", id = it) }
            val orders = ids.map { uid ->
                Order.create(
                    userId = uid,
                    lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                    idempotencyKey = "k-$uid",
                )
            }
            every { orderRepository.findAllForAdmin(0, size) } returns PageResult(orders, 0, size, size.toLong(), 1)
            every { userRepository.findAllByIds(any()) } returns users

            val result = orderFacade.getOrdersForAdmin(0, size)

            assertThat(result.content).hasSize(size)
            verify(exactly = 1) { userRepository.findAllByIds(any()) }
            verify(exactly = 0) { userRepository.findById(any()) }
        }
    }

    @Nested
    @DisplayName("getOrderForAdmin — UC-5")
    inner class GetOrderForAdmin {
        @Test
        @DisplayName("어느 회원 주문이든 AdminOrderResult 반환 (FORBIDDEN 분기 없음)")
        fun returnsAnyUsersOrder() {
            val user = UserFixture.validUser()
            val order = Order.create(
                userId = user.id,
                lines = listOf(OrderLine.create(1L, "P", 1000, 1)),
                idempotencyKey = "k",
            ).also {
                it.markPaymentPending()
                it.markPaid("tx", "OK")
            }
            every { orderRepository.findById(10L) } returns order
            every { userRepository.findById(user.id) } returns user

            val result = orderFacade.getOrderForAdmin(10L)

            assertThat(result.orderId).isEqualTo(order.id)
        }

        @Test
        @DisplayName("존재하지 않으면 ORDER_NOT_FOUND")
        fun notFound() {
            every { orderRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { orderFacade.getOrderForAdmin(99L) }
            assertThat(ex.errorType).isEqualTo(OrderErrorType.ORDER_NOT_FOUND)
        }
    }
}
