package com.loopers.interfaces.api

import com.loopers.config.redis.RedisConfig
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.RankingKey
import com.loopers.interfaces.api.product.ProductV1Dto
import com.loopers.interfaces.api.ranking.RankingV1Dto
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import java.time.LocalDate
import java.time.ZoneId

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig::class)
class RankingV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val jdbcTemplate: JdbcTemplate,
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private val masterTemplate: RedisTemplate<String, String>,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    private fun todayKey() = RankingKey.of(LocalDate.now(ZoneId.of("Asia/Seoul")))

    private fun seedScore(productId: Long, score: Double) {
        masterTemplate.opsForZSet().add(todayKey(), productId.toString(), score)
    }

    private fun seedMv(table: String, periodKey: String, rankNo: Int, productId: Long, score: Double) {
        jdbcTemplate.update(
            "INSERT INTO $table (period_key, rank_no, product_id, score, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, NOW(6), NOW(6))",
            periodKey,
            rankNo,
            productId,
            score,
        )
    }

    @Test
    @DisplayName("GET /api/v1/rankings 는 점수 내림차순으로 상품 정보가 조립된 목록을 200 으로 반환한다")
    fun returnsRankingDesc() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val lower = productRepository.save(ProductFixture.validProduct(name = "A", price = 1000, brandId = brandId)).id
        val higher = productRepository.save(ProductFixture.validProduct(name = "B", price = 2000, brandId = brandId)).id
        seedScore(lower, 1.0)
        seedScore(higher, 3.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val content = response.body!!.data!!.content
        assertThat(content.map { it.productId }).containsExactly(higher, lower)
        assertThat(content.first().rank).isEqualTo(1L)
        assertThat(content.first().brandName).isEqualTo("나이키")
    }

    @Test
    @DisplayName("date 를 지정하면 그 날짜의 랭킹판이 조회된다")
    fun returnsRankingForGivenDate() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val product = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id
        // 오늘이 아닌 특정 날짜 랭킹판에만 점수를 넣는다.
        masterTemplate.opsForZSet().add("rank:all:20260101", product.toString(), 5.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=20260101",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.content.map { it.productId }).containsExactly(product)
    }

    @Test
    @DisplayName("date 형식이 yyyyMMdd 가 아니면 400 RANKING_BAD_REQUEST 를 반환한다")
    fun rejectsMalformedDate() {
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=2026-07-14",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.meta?.errorCode).isEqualTo("RANKING_BAD_REQUEST")
    }

    @Test
    @DisplayName("period 가 잘못된 값이면 400 RANKING_BAD_REQUEST 를 반환한다")
    fun rejectsUnknownPeriod() {
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=yearly",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(response.body?.meta?.errorCode).isEqualTo("RANKING_BAD_REQUEST")
    }

    @Test
    @DisplayName("period 는 대소문자 구분 없이 해석된다 - 소문자 daily 는 기존 일간 조회와 동일하다")
    fun acceptsLowercasePeriod() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val product = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id
        seedScore(product, 5.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=daily",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.content.map { it.productId }).containsExactly(product)
    }

    @Test
    @DisplayName("period=WEEKLY 는 date 가 속한 주의 MV 에서 상품정보가 조립된 랭킹을 반환한다")
    fun returnsWeeklyRankingFromMv() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val first = productRepository.save(ProductFixture.validProduct(name = "A", price = 1000, brandId = brandId)).id
        val second = productRepository.save(ProductFixture.validProduct(name = "B", price = 2000, brandId = brandId)).id
        // 2026-07-21 이 속한 ISO 주 = 2026W30
        seedMv("mv_product_rank_weekly", "2026W30", 1, first, 9.9)
        seedMv("mv_product_rank_weekly", "2026W30", 2, second, 5.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY&date=20260721",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val content = response.body!!.data!!.content
        assertThat(content.map { it.productId }).containsExactly(first, second)
        assertThat(content.map { it.rank }).containsExactly(1L, 2L)
        assertThat(content.first().score).isEqualTo(9.9)
        assertThat(content.first().brandName).isEqualTo("나이키")
        assertThat(content.first().name).isEqualTo("A")
    }

    @Test
    @DisplayName("period=MONTHLY 는 date 가 속한 달의 MV 에서 조회한다")
    fun returnsMonthlyRankingFromMv() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val product = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id
        seedMv("mv_product_rank_monthly", "202607", 1, product, 7.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=monthly&date=20260721",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.content.map { it.productId }).containsExactly(product)
        assertThat(response.body!!.data!!.totalElements).isEqualTo(1L)
    }

    @Test
    @DisplayName("주간 MV 에 있으나 삭제된 상품은 목록에서 제외된다")
    fun weeklyRankingSkipsMissingProduct() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val alive = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id
        seedMv("mv_product_rank_weekly", "2026W30", 1, alive, 9.9)
        // 존재하지 않는 상품 — 조립에서 스킵된다.
        seedMv("mv_product_rank_weekly", "2026W30", 2, 999_999L, 5.0)

        val response = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY&date=20260721",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.content.map { it.productId }).containsExactly(alive)
    }

    @Test
    @DisplayName("보존 기간 밖(존재하지 않는) 날짜는 200 과 빈 목록을 반환한다")
    fun returnsEmptyForExpiredDate() {
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?date=20200101",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.content).isEmpty()
        assertThat(response.body!!.data!!.totalElements).isEqualTo(0L)
    }

    @Test
    @DisplayName("size 가 상한을 넘으면 상한값(100)으로 보정된다")
    fun capsOversizedPageSize() {
        val response = testRestTemplate.exchange(
            "/api/v1/rankings?size=999",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingsResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.size).isEqualTo(100)
    }

    @Test
    @DisplayName("상품 상세 응답에 오늘 랭킹판 순위가 포함된다")
    fun productDetailIncludesRank() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val product = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id
        seedScore(product, 5.0)

        val response = testRestTemplate.exchange(
            "/api/v1/products/$product",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.rank).isEqualTo(1L)
    }

    @Test
    @DisplayName("랭킹판에 없는 상품의 상세 순위는 null 이다")
    fun productDetailRankNullWhenNotRanked() {
        val brandId = brandRepository.save(BrandFixture.validBrand("나이키")).id
        val product = productRepository.save(ProductFixture.validProduct(name = "A", brandId = brandId)).id

        val response = testRestTemplate.exchange(
            "/api/v1/products/$product",
            HttpMethod.GET,
            null,
            object : ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>>() {},
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.data!!.rank).isNull()
    }
}
