package com.loopers.application.order

import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.domain.order.OrderStatus
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.User
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.infrastructure.order.OrderJpaRepository
import com.loopers.support.error.CoreException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 주문 동시성 — 재고 차감(비관 락)·쿠폰 단일 사용(비관 락)이 동시/중복 요청에도 정확히 동작하는지
 * 실제 영속 계층으로 검증한다. 주문 생성은 생성(CREATED) 상태까지이며, 결제는 별도 흐름이다.
 */
@SpringBootTest
class OrderConcurrencyIntegrationTest @Autowired constructor(
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

    private fun newUsers(count: Int): List<User> =
        (0 until count).map {
            userRepository.save(UserFixture.validUser(loginId = "buyer$it", email = "buyer$it@example.com"))
        }

    private fun issuedCoupon(userId: Long): UserCoupon {
        val template = couponRepository.save(
            CouponFixture.coupon(name = "10% 할인", discountValue = 10, minOrderAmount = null),
        )
        return userCouponRepository.save(
            UserCoupon.issue(
                userId = userId,
                couponId = template.id,
                usableFrom = LocalDateTime.now().minusDays(1),
                expiredAt = LocalDateTime.now().plusDays(30),
            ),
        )
    }

    /** 각 스레드의 결과(성공=null / 예외)를 인덱스별로 수집한다. */
    private fun runConcurrently(threads: Int, block: (Int) -> Unit): List<Throwable?> {
        val errors = arrayOfNulls<Throwable>(threads)
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(threads)
        val executor = Executors.newFixedThreadPool(threads)
        repeat(threads) { i ->
            executor.submit {
                try {
                    startLatch.await()
                    block(i)
                } catch (t: Throwable) {
                    errors[i] = t
                } finally {
                    doneLatch.countDown()
                }
            }
        }
        startLatch.countDown()
        doneLatch.await(30, TimeUnit.SECONDS)
        executor.shutdown()
        return errors.toList()
    }

    @DisplayName("[5] 서로 다른 사용자가 같은 상품을 동시에 주문하면, 재고가 정확히 차감되고 모든 주문이 생성(CREATED) 상태로 생성된다.")
    @Test
    fun concurrentOrdersByDifferentUsersDeductStockAndSucceed() {
        val orderers = 8
        val users = newUsers(orderers)
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = orderers))

        val errors = runConcurrently(orderers) { i ->
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = users[i].loginId,
                    idempotencyKey = "c5-$i",
                    userCouponId = null,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                ),
            )
        }

        assertThat(errors.filterNotNull()).isEmpty()
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(0)
        assertThat(orderJpaRepository.count()).isEqualTo(orderers.toLong())
        assertThat(orderJpaRepository.findAll()).allMatch { it.status == OrderStatus.CREATED }
    }

    @DisplayName("[6] 같은 사용자가 같은 쿠폰으로 동시에 여러 번 주문하면, 쿠폰은 한 번만 사용되고 나머지 주문은 ALREADY_USED_COUPON 으로 실패한다.")
    @Test
    fun concurrentOrdersBySameUserConsumeCouponOnce() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(name = "운동화", price = 1000, stock = 100))
        val coupon = issuedCoupon(user.id)

        val attempts = 8
        val errors = runConcurrently(attempts) { i ->
            orderFacade.placeOrder(
                PlaceOrderCommand(
                    loginId = user.loginId,
                    idempotencyKey = "c6-$i",
                    userCouponId = coupon.id,
                    lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
                ),
            )
        }

        val alreadyUsed = errors.filterIsInstance<CoreException>()
            .count { it.errorType == CouponErrorType.ALREADY_USED_COUPON }
        assertThat(alreadyUsed).isEqualTo(attempts - 1)
        assertThat(userCouponRepository.findById(coupon.id)!!.status).isEqualTo(UserCouponStatus.USED)
        assertThat(orderJpaRepository.count()).isEqualTo(1L)
        assertThat(productRepository.findById(product.id)!!.stock.value).isEqualTo(99)
    }
}
