package com.loopers.infrastructure.product

import com.loopers.application.product.port.CachedProductDetail
import com.loopers.application.product.port.ProductCache
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

/**
 * 실제 Redis(Testcontainer) + Spring 와이어링으로 직렬화·키·라운드트립을 검증한다.
 * 빈 주입(@Qualifier master RedisTemplate, ObjectMapper) 정상 여부도 함께 확인된다.
 */
@SpringBootTest
@Import(RedisTestContainersConfig::class)
class RedisProductCacheIntegrationTest @Autowired constructor(
    private val productCache: ProductCache,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("상세 put 후 get 으로 동일 본문이 복원된다")
    fun detailRoundtrip() {
        val detail = CachedProductDetail(1L, "운동화", 59_000L, 3L, 9L, "나이키")
        productCache.putDetail(detail)

        assertThat(productCache.getDetail(1L)).isEqualTo(detail)
    }

    @Test
    @DisplayName("상세 evict 후 get 은 null(miss) 이다")
    fun evictRemoves() {
        productCache.putDetail(CachedProductDetail(2L, "구두", 80_000L, 0L, 9L, "나이키"))

        productCache.evictDetail(2L)

        assertThat(productCache.getDetail(2L)).isNull()
    }

    @Test
    @DisplayName("목록 put 후 get 으로 PageResult 가 복원되며, 다른 정렬/브랜드 키는 miss 다")
    fun listRoundtripAndKeyIsolation() {
        val page = PageResult(
            content = listOf(ProductSummaryResult(1L, "운동화", 59_000L, 3L, 9L)),
            page = 0,
            size = 20,
            totalElements = 1L,
            totalPages = 1,
        )
        productCache.putList(null, ProductSortType.LIKES_DESC, 0, 20, page)

        assertThat(productCache.getList(null, ProductSortType.LIKES_DESC, 0, 20)).isEqualTo(page)
        assertThat(productCache.getList(null, ProductSortType.LATEST, 0, 20)).isNull()
        assertThat(productCache.getList(7L, ProductSortType.LIKES_DESC, 0, 20)).isNull()
    }

    @Test
    @DisplayName("미스 시 null 을 반환한다")
    fun missReturnsNull() {
        assertThat(productCache.getDetail(99_999L)).isNull()
    }
}
