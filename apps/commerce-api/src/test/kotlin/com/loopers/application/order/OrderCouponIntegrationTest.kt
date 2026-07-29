package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 주문 ↔ 쿠폰 교차 도메인 통합 — 같은 트랜잭션 안에서 쿠폰이 소진(USED)되고 할인이 반영되며,
 * 쿠폰 적용 실패 시 주문 전체가 롤백(재고 미차감)되는지 실제 영속 계층으로 검증한다.
 */
@SpringBootTest
class OrderCouponIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val orderJpaRepository: OrderJpaRepository,
    private val databaseCleanUp: com.loopers.utils.DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun newUser(loginId: String = UserFixture.DEFAULT_LOGIN_ID, email: String = UserFixture.DEFAULT_EMAIL) =
        userRepository.save(UserFixture.validUser(loginId = loginId, email = email))

    private fun newProduct(price: Long = 1000, stock: Int = 10) =
        productRepository.save(ProductFixture.validProduct(name = "운동화", price = price, stock = stock))

    private fun newRateTemplate(value: Long = 10) =
        couponRepository.save(
            CouponFixture.coupon(
                name = "10% 할인",
                discountType = DiscountType.RATE,
                discountValue = value,
                minOrderAmount = null,
            ),
        )

    // 발급 쿠폰 도메인 객체 생성(저장은 호출부에서). 사용 가능 구간을 인자로 스냅샷한다.
    private fun issue(
        userId: Long,
        couponId: Long,
        usableFrom: LocalDateTime = LocalDateTime.now().minusDays(1),
        expiredAt: LocalDateTime = LocalDateTime.now().plusDays(30),
    ): UserCoupon = UserCoupon.issue(userId = userId, couponId = couponId, usableFrom = usableFrom, expiredAt = expiredAt)

    @DisplayName("쿠폰을 적용해 주문하면, 할인이 반영되고 쿠폰이 즉시 USED 로 소진된다.")
    @Test
    fun appliesAndConsumesCoupon() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)
        val template = newRateTemplate(value = 10)
        val userCoupon = userCouponRepository.save(issue(userId = user.id, couponId = template.id))

        val result = orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "order-1",
                userCouponId = userCoupon.id,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
            ),
        )

        // 원 합계 2000, 10% 할인 → 200 차감 → 1800
        assertThat(result.userCouponId).isEqualTo(userCoupon.id)
        assertThat(result.originalAmount).isEqualTo(2000L)
        assertThat(result.discountAmount).isEqualTo(200L)
        assertThat(result.totalAmount).isEqualTo(1800L)
        assertThat(result.status).isEqualTo(OrderStatus.CREATED)
        // 쿠폰은 즉시 USED 로 소진된다.
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.USED)
        // 주문은 생성 상태로 저장되고 종료된다
        val persisted = orderJpaRepository.findById(result.orderId).get()
        assertThat(persisted.status).isEqualTo(OrderStatus.CREATED)
    }

    @DisplayName("이미 사용된 쿠폰으로 주문하면 실패하고, 주문 생성·재고 차감이 모두 롤백된다.")
    @Test
    fun rollsBackWhenCouponAlreadyUsed() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)
        val template = newRateTemplate()
        val userCoupon = userCouponRepository.save(issue(userId = user.id, couponId = template.id))
            .also { it.use(LocalDateTime.now()) }
            .also { userCouponRepository.save(it) }

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "order-2",
                    userCouponId = userCoupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.ALREADY_USED_COUPON)
        // 주문 미생성 + 재고 미차감(롤백).
        assertThat(orderJpaRepository.count()).isEqualTo(0L)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
    }

    @DisplayName("타 유저 소유 쿠폰으로 주문하면 USER_COUPON_NOT_FOUND 로 실패하고 재고가 차감되지 않는다.")
    @Test
    fun rejectsOtherUsersCoupon() {
        val owner = newUser(loginId = "owner", email = "owner@example.com")
        val requester = newUser(loginId = "requester", email = "requester@example.com")
        val product = newProduct(price = 1000, stock = 10)
        val template = newRateTemplate()
        val othersCoupon = userCouponRepository.save(issue(userId = owner.id, couponId = template.id))

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = requester.loginId,
                    idempotencyKey = "order-3",
                    userCouponId = othersCoupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.USER_COUPON_NOT_FOUND)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        // 소유자의 쿠폰은 그대로 사용 가능 상태를 유지한다.
        assertThat(userCouponRepository.findById(othersCoupon.id)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("만료된 쿠폰으로 주문하면 COUPON_NOT_APPLICABLE 로 실패하고 재고가 차감되지 않는다.")
    @Test
    fun rollsBackWhenCouponExpired() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)
        // 발급 당시엔 유효했으나 이후 사용 만료된 발급 쿠폰을 모사한다 — 만료는 발급 쿠폰의 expiredAt(스냅샷)으로 판정된다.
        val template = couponRepository.save(CouponFixture.coupon(name = "만료"))
        val userCoupon = userCouponRepository.save(
            issue(
                userId = user.id,
                couponId = template.id,
                usableFrom = LocalDateTime.now().minusDays(30),
                expiredAt = LocalDateTime.now().minusDays(1),
            ),
        )

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "order-expired",
                    userCouponId = userCoupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        assertThat(orderJpaRepository.count()).isEqualTo(0L)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("상품 합계가 최소 주문 금액에 못 미치면 COUPON_NOT_APPLICABLE 로 실패하고 재고가 차감되지 않는다.")
    @Test
    fun rollsBackWhenBelowMinOrderAmount() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)
        val template = couponRepository.save(
            CouponFixture.coupon(
                name = "최소금액쿠폰",
                discountType = DiscountType.RATE,
                discountValue = 10,
                // 최소 주문 금액(100,000) 을 상품 합계(2,000) 보다 크게 둔다.
                minOrderAmount = 100_000,
            ),
        )
        val userCoupon = userCouponRepository.save(issue(userId = user.id, couponId = template.id))

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "order-min",
                    userCouponId = userCoupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.AVAILABLE)
    }

    @DisplayName("존재하지 않는 발급 쿠폰 식별자로 주문하면 USER_COUPON_NOT_FOUND 로 실패하고 재고가 차감되지 않는다.")
    @Test
    fun rollsBackWhenUserCouponNotFound() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)

        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "order-missing",
                    userCouponId = 999_999L,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 2)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.USER_COUPON_NOT_FOUND)
        assertThat(orderJpaRepository.count()).isEqualTo(0L)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(10)
    }

    @DisplayName("쿠폰을 적용한 주문이 성공하면, 같은 쿠폰으로 다른 주문을 다시 시도해도 ALREADY_USED_COUPON 로 차단된다 (재사용 불가).")
    @Test
    fun blocksReuseAfterSuccessfulOrder() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 10)
        val template = newRateTemplate()
        val userCoupon = userCouponRepository.save(issue(userId = user.id, couponId = template.id))

        orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "order-first",
                userCouponId = userCoupon.id,
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
            ),
        )

        // 다른 멱등 키로 새 주문을 시도한다(같은 쿠폰 재사용).
        val ex = assertThrows<CoreException> {
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "order-second",
                    userCouponId = userCoupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                ),
            )
        }

        assertThat(ex.errorType).isEqualTo(CouponErrorType.ALREADY_USED_COUPON)
        // 첫 주문만 성립(order-first), 두 번째는 미생성 → 재고는 1회만 차감.
        assertThat(orderJpaRepository.count()).isEqualTo(1L)
        assertThat(orderJpaRepository.findAll().single().idempotencyKey).isEqualTo("order-first")
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(9)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.USED)
    }

    @DisplayName("같은 발급 쿠폰으로 여러 주문을 동시에 요청해도, 쿠폰은 정확히 한 번만 소진되고 주문도 한 건만 성립한다.")
    @Test
    fun concurrentOrdersConsumeCouponOnce() {
        val user = newUser()
        val product = newProduct(price = 1000, stock = 100)
        val template = newRateTemplate()
        val userCoupon = userCouponRepository.save(issue(userId = user.id, couponId = template.id))

        val threads = 6
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val success = AtomicInteger(0)
        val alreadyUsed = AtomicInteger(0)
        val executor = Executors.newFixedThreadPool(threads)

        repeat(threads) { i ->
            executor.submit {
                try {
                    startLatch.await()
                    // 서로 다른 멱등 키 = 같은 쿠폰을 다투는 별개 주문.
                    orderFacade.placeOrder(
                        PlaceOrderCommand(
                            loginId = user.loginId,
                            idempotencyKey = "concurrent-$i",
                            userCouponId = userCoupon.id,
                            lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                        ),
                    )
                    success.incrementAndGet()
                } catch (e: CoreException) {
                    if (e.errorType == CouponErrorType.ALREADY_USED_COUPON) alreadyUsed.incrementAndGet()
                } finally {
                    doneLatch.countDown()
                }
            }
        }

        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()

        // 정확히 1건만 성공, 쿠폰은 1회 소진, 주문도 1건, 재고도 1회만 차감.
        assertThat(success.get()).isEqualTo(1)
        assertThat(alreadyUsed.get()).isEqualTo(threads - 1)
        assertThat(userCouponRepository.findById(userCoupon.id)!!.status).isEqualTo(UserCouponStatus.USED)
        assertThat(orderJpaRepository.count()).isEqualTo(1L)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(99)
    }
}
