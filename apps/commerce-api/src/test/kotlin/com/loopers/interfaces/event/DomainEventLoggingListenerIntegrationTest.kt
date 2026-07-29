package com.loopers.interfaces.event

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.loopers.application.order.OrderFacade
import com.loopers.application.order.command.OrderLineCommand
import com.loopers.application.order.command.PlaceOrderCommand
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.user.UserFixture
import com.loopers.domain.user.UserRepository
import com.loopers.utils.DatabaseCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Duration

/**
 * 도메인 이벤트 서버 로깅 — 주문이 커밋되면 그 사실이 커밋 이후(AFTER_COMMIT) 서버 로그로 남는지 검증한다.
 * 로깅은 도메인 이벤트의 또 다른 소비자(리스너)일 뿐, 별도 발행 경로가 아니다.
 */
@SpringBootTest
class DomainEventLoggingListenerIntegrationTest @Autowired constructor(
    private val orderFacade: OrderFacade,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    private val appender = ListAppender<ILoggingEvent>()
    private lateinit var logger: Logger

    @BeforeEach
    fun attachAppender() {
        logger = LoggerFactory.getLogger(DomainEventLoggingListener::class.java) as Logger
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(appender)
        databaseCleanUp.truncateAllTables()
    }

    @Test
    fun `주문이 커밋되면 도메인 이벤트가 서버 로그로 남는다`() {
        val user = userRepository.save(UserFixture.validUser())
        val product = productRepository.save(ProductFixture.validProduct(price = 1000, stock = 10))

        orderFacade.placeOrder(
            PlaceOrderCommand(
                loginId = user.loginId,
                idempotencyKey = "order-log-1",
                lines = listOf(OrderLineCommand(productId = product.id, quantity = 1)),
            ),
        )

        await().atMost(Duration.ofSeconds(5)).untilAsserted {
            assertThat(appender.list.map { it.formattedMessage })
                .anyMatch { it.contains("OrderEvent") }
        }
    }
}
