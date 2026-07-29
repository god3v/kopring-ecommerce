package com.loopers.infrastructure.product

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.loopers.application.product.port.CachedProductDetail
import com.loopers.application.product.port.ProductCache
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.config.redis.RedisConfig
import com.loopers.domain.product.ProductSortType
import com.loopers.support.page.PageResult
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 상품 조회 Redis 캐시 어댑터.
 * 모든 호출은 저장소 장애를 흡수한다 — 읽기는 miss, 쓰기/삭제는 no-op 로 폴백해 Redis 다운에도 DB 로 동작한다.
 */
@Component
class RedisProductCache(
    // 읽기: replica 분산 템플릿 — read 트래픽을 master 에서 덜어낸다.
    private val readTemplate: RedisTemplate<String, String>,
    // 쓰기/삭제: master — 즉시 반영.
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val writeTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) : ProductCache {
    private val log = LoggerFactory.getLogger(javaClass)
    private val listType = object : TypeReference<PageResult<ProductSummaryResult>>() {}

    override fun getDetail(productId: Long): CachedProductDetail? =
        read(detailKey(productId)) { objectMapper.readValue(it, CachedProductDetail::class.java) }

    override fun putDetail(detail: CachedProductDetail) =
        write(detailKey(detail.id), detail, DETAIL_TTL)

    override fun evictDetail(productId: Long) =
        evict(detailKey(productId))

    override fun getList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
    ): PageResult<ProductSummaryResult>? =
        read(listKey(brandId, sort, page, size)) { objectMapper.readValue(it, listType) }

    override fun putList(
        brandId: Long?,
        sort: ProductSortType,
        page: Int,
        size: Int,
        result: PageResult<ProductSummaryResult>,
    ) = write(listKey(brandId, sort, page, size), result, LIST_TTL)

    private fun <T> read(key: String, deserialize: (String) -> T): T? = runCatching {
        readTemplate.opsForValue().get(key)?.let(deserialize)
    }.getOrElse {
        log.warn("cache get 실패 (key={})", key, it)
        null
    }

    private fun write(key: String, value: Any, ttl: Duration) {
        runCatching { writeTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl) }
            .onFailure { log.warn("cache put 실패 (key={})", key, it) }
    }

    private fun evict(key: String) {
        runCatching { writeTemplate.delete(key) }
            .onFailure { log.warn("cache evict 실패 (key={})", key, it) }
    }

    private fun detailKey(productId: Long): String = "product:detail:v1:$productId"

    private fun listKey(brandId: Long?, sort: ProductSortType, page: Int, size: Int): String =
        "product:list:v1:${brandId ?: "all"}:${sort.key}:$page:$size"

    companion object {
        private val DETAIL_TTL: Duration = Duration.ofMinutes(5)
        private val LIST_TTL: Duration = Duration.ofSeconds(60)
    }
}
