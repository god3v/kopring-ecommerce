package com.loopers.application.ranking

import com.loopers.application.ranking.result.RankedProductResult
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.PeriodRankingRepository
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class RankingFacadeTest {
    private val rankingRepository = mockk<RankingRepository>(relaxed = true)
    private val periodRankingRepository = mockk<PeriodRankingRepository>(relaxed = true)
    private val productRepository = mockk<ProductRepository>()
    private val brandRepository = mockk<BrandRepository>()
    private val facade = RankingFacade(rankingRepository, periodRankingRepository, productRepository, brandRepository)

    @DisplayName("일간 랭킹 조회는,")
    @Nested
    inner class GetDailyRanking {
        @Test
        fun `점수 내림차순으로 상품 정보가 조립된 목록을 반환한다`() {
            every { rankingRepository.topN("rank:all:20260714", 0, 20) } returns listOf(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
            )
            every { productRepository.findAllByIds(listOf(202L, 303L)) } returns listOf(
                ProductFixture.validProduct(id = 202L, name = "B", price = 2000, likeCount = 5, brandId = 9L),
                ProductFixture.validProduct(id = 303L, name = "C", price = 3000, likeCount = 1, brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(
                BrandFixture.validBrand(id = 9L, name = "나이키"),
            )

            val result = facade.getRanking(period = RankingPeriod.DAILY, date = "20260714", page = 0, size = 20)

            assertThat(result.content).containsExactly(
                RankedProductResult(productId = 202L, name = "B", price = 2000, brandName = "나이키", likeCount = 5, rank = 1, score = 3.0),
                RankedProductResult(productId = 303L, name = "C", price = 3000, brandName = "나이키", likeCount = 1, rank = 2, score = 2.0),
            )
        }

        @Test
        fun `날짜 미지정 시 오늘 랭킹판을 조회한다`() {
            val todayKey = RankingKey.of(LocalDate.now(ZoneId.of("Asia/Seoul")))
            every { rankingRepository.topN(todayKey, 0, 20) } returns emptyList()

            facade.getRanking(period = RankingPeriod.DAILY, date = null, page = 0, size = 20)

            verify { rankingRepository.topN(todayKey, 0, 20) }
        }

        @Test
        fun `순위는 페이지를 넘어 이어진다 - 2페이지 첫 항목은 page 곱하기 size 더하기 1`() {
            every { rankingRepository.topN("rank:all:20260714", 2, 2) } returns listOf(
                RankedEntry(productId = 404L, score = 3.0),
                RankedEntry(productId = 505L, score = 2.0),
            )
            every { productRepository.findAllByIds(listOf(404L, 505L)) } returns listOf(
                ProductFixture.validProduct(id = 404L, brandId = 9L),
                ProductFixture.validProduct(id = 505L, brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(BrandFixture.validBrand(id = 9L, name = "나이키"))

            val result = facade.getRanking(period = RankingPeriod.DAILY, date = "20260714", page = 1, size = 2)

            assertThat(result.content.map { it.rank }).containsExactly(3L, 4L)
        }

        @Test
        fun `랭킹판에 있으나 삭제된 상품은 목록에서 제외된다`() {
            every { rankingRepository.topN("rank:all:20260714", 0, 20) } returns listOf(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
            )
            // 202 는 삭제되어 조회되지 않는다 — 303 만 반환된다.
            every { productRepository.findAllByIds(listOf(202L, 303L)) } returns listOf(
                ProductFixture.validProduct(id = 303L, name = "C", brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(BrandFixture.validBrand(id = 9L, name = "나이키"))

            val result = facade.getRanking(period = RankingPeriod.DAILY, date = "20260714", page = 0, size = 20)

            assertThat(result.content.map { it.productId }).containsExactly(303L)
            assertThat(result.content.single().rank).isEqualTo(2L)
        }

        @Test
        fun `전체 페이지 수는 총 개수를 페이지 크기로 올림해 계산된다`() {
            every { rankingRepository.size("rank:all:20260714") } returns 41L
            every { rankingRepository.topN("rank:all:20260714", 0, 20) } returns emptyList()

            val result = facade.getRanking(period = RankingPeriod.DAILY, date = "20260714", page = 0, size = 20)

            assertThat(result.totalPages).isEqualTo(3)
            assertThat(result.totalElements).isEqualTo(41L)
        }

        @Test
        fun `랭킹판이 없는 날짜는 빈 목록을 반환한다`() {
            every { rankingRepository.topN("rank:all:20200101", 0, 20) } returns emptyList()

            val result = facade.getRanking(period = RankingPeriod.DAILY, date = "20200101", page = 0, size = 20)

            assertThat(result.content).isEmpty()
        }
    }

    @DisplayName("주간·월간 랭킹 조회는,")
    @Nested
    inner class GetPeriodRanking {
        @Test
        fun `날짜가 속한 주의 MV 에서 상품 정보가 조립된 목록을 반환한다`() {
            // 2026-07-21 → ISO 주 2026W30
            every { periodRankingRepository.topN(RankingPeriod.WEEKLY, "2026W30", 0, 20) } returns listOf(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
            )
            every { periodRankingRepository.size(RankingPeriod.WEEKLY, "2026W30") } returns 2L
            every { productRepository.findAllByIds(listOf(202L, 303L)) } returns listOf(
                ProductFixture.validProduct(id = 202L, name = "B", price = 2000, likeCount = 5, brandId = 9L),
                ProductFixture.validProduct(id = 303L, name = "C", price = 3000, likeCount = 1, brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(BrandFixture.validBrand(id = 9L, name = "나이키"))

            val result = facade.getRanking(period = RankingPeriod.WEEKLY, date = "20260721", page = 0, size = 20)

            assertThat(result.content).containsExactly(
                RankedProductResult(productId = 202L, name = "B", price = 2000, brandName = "나이키", likeCount = 5, rank = 1, score = 3.0),
                RankedProductResult(productId = 303L, name = "C", price = 3000, brandName = "나이키", likeCount = 1, rank = 2, score = 2.0),
            )
            assertThat(result.totalElements).isEqualTo(2L)
        }

        @Test
        fun `월간은 날짜가 속한 달의 키로 조회한다`() {
            every { periodRankingRepository.topN(RankingPeriod.MONTHLY, "202607", 0, 20) } returns emptyList()

            facade.getRanking(period = RankingPeriod.MONTHLY, date = "20260721", page = 0, size = 20)

            verify { periodRankingRepository.topN(RankingPeriod.MONTHLY, "202607", 0, 20) }
        }

        @Test
        fun `주간 순위도 페이지를 넘어 이어진다 - MV 의 rank_no 와 일치한다`() {
            // rank_no 가 1부터 빈틈없이 적재되므로 offset 기반 rank 계산이 rank_no 와 같다.
            every { periodRankingRepository.topN(RankingPeriod.WEEKLY, "2026W30", 1, 2) } returns listOf(
                RankedEntry(productId = 404L, score = 3.0),
                RankedEntry(productId = 505L, score = 2.0),
            )
            every { productRepository.findAllByIds(listOf(404L, 505L)) } returns listOf(
                ProductFixture.validProduct(id = 404L, brandId = 9L),
                ProductFixture.validProduct(id = 505L, brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(BrandFixture.validBrand(id = 9L, name = "나이키"))

            val result = facade.getRanking(period = RankingPeriod.WEEKLY, date = "20260721", page = 1, size = 2)

            assertThat(result.content.map { it.rank }).containsExactly(3L, 4L)
        }

        @Test
        fun `MV 에 있으나 삭제된 상품은 목록에서 제외된다`() {
            every { periodRankingRepository.topN(RankingPeriod.WEEKLY, "2026W30", 0, 20) } returns listOf(
                RankedEntry(productId = 202L, score = 3.0),
                RankedEntry(productId = 303L, score = 2.0),
            )
            every { productRepository.findAllByIds(listOf(202L, 303L)) } returns listOf(
                ProductFixture.validProduct(id = 303L, name = "C", brandId = 9L),
            )
            every { brandRepository.findAllByIds(listOf(9L)) } returns listOf(BrandFixture.validBrand(id = 9L, name = "나이키"))

            val result = facade.getRanking(period = RankingPeriod.WEEKLY, date = "20260721", page = 0, size = 20)

            assertThat(result.content.map { it.productId }).containsExactly(303L)
        }
    }
}
