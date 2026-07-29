package com.loopers.infrastructure.product

import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.port.CachedProductDetail
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate

/**
 * 저장소 장애 흡수 — Redis 호출이 실패해도 get 은 null(miss), put/evict 은 no-op 로 폴백해야 한다.
 * 이로써 Facade read-through 가 Redis 다운 상황에서도 DB 로 정상 동작한다(체크리스트: 캐시 미스에도 정상 동작).
 */
@DisplayName("RedisProductCache — 저장소 장애 흡수")
class RedisProductCacheTest {
    private val readTemplate: RedisTemplate<String, String> = mockk()
    private val writeTemplate: RedisTemplate<String, String> = mockk()
    private val cache = RedisProductCache(readTemplate, writeTemplate, ObjectMapper())

    @Test
    @DisplayName("get 중 Redis 예외가 나면 null(miss) 로 폴백한다")
    fun getSwallowsFailure() {
        every { readTemplate.opsForValue() } throws RuntimeException("redis down")

        assertThat(cache.getDetail(1L)).isNull()
        assertThat(cache.getList(null, ProductSortType.LATEST, 0, 20)).isNull()
    }

    @Test
    @DisplayName("put / evict 중 Redis 예외가 나도 예외를 던지지 않는다(no-op)")
    fun writeAndEvictSwallowFailure() {
        every { writeTemplate.opsForValue() } throws RuntimeException("redis down")
        every { writeTemplate.delete(any<String>()) } throws RuntimeException("redis down")

        assertThatCode {
            cache.putDetail(CachedProductDetail(1L, "n", 1000L, 0L, 9L, "b"))
            cache.putList(null, ProductSortType.LATEST, 0, 20, PageResult(emptyList(), 0, 20, 0, 0))
            cache.evictDetail(1L)
        }.doesNotThrowAnyException()
    }
}
