package com.loopers.application.ranking

import com.loopers.application.ranking.result.RankedProductResult
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.ranking.PeriodRankingRepository
import com.loopers.domain.ranking.RankedEntry
import com.loopers.domain.ranking.RankingDate
import com.loopers.domain.ranking.RankingKey
import com.loopers.domain.ranking.RankingPeriod
import com.loopers.domain.ranking.RankingPeriodKey
import com.loopers.domain.ranking.RankingRepository
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 랭킹판 조회를 상품 정보와 조립한다 — 일간은 Redis 랭킹판, 주간·월간은 배치가 적재한 MV 에서 순위·점수를 읽고,
 * 상품·브랜드를 배치 조회해 목록에 필요한 정보를 붙인다. 저장소가 달라도 조립은 같은 경로를 공유한다.
 * 삭제/부재 상품은 목록에서 제외한다. N+1 없이 id 묶음으로 한 번에 조회한다.
 *
 * 의도적으로 트랜잭션을 걸지 않는다 — 경계를 메서드에 걸면 Redis I/O(size·topN) 대기 동안 DB 커넥션을 점유해
 * 지연 시 풀 고갈로 번진다. 상품·브랜드 배치 조회는 각자 저장소 기본 readOnly 트랜잭션으로 충분하고,
 * 두 조회 사이의 정합은 부재 상품 스킵 방어가 이미 흡수한다.
 */
@Component
class RankingFacade(
    private val rankingRepository: RankingRepository,
    private val periodRankingRepository: PeriodRankingRepository,
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
) {
    fun getRanking(period: RankingPeriod, date: String?, page: Int, size: Int): PageResult<RankedProductResult> {
        val targetDate = RankingDate.resolve(date, LocalDate.now(SEOUL))
        return when (period) {
            RankingPeriod.DAILY -> dailyRanking(targetDate, page, size)
            RankingPeriod.WEEKLY -> periodRanking(period, RankingPeriodKey.weeklyOf(targetDate), page, size)
            RankingPeriod.MONTHLY -> periodRanking(period, RankingPeriodKey.monthlyOf(targetDate), page, size)
        }
    }

    private fun dailyRanking(date: LocalDate, page: Int, size: Int): PageResult<RankedProductResult> {
        val key = RankingKey.of(date)
        val total = rankingRepository.size(key)
        val offset = page.toLong() * size
        val entries = rankingRepository.topN(key, offset, size.toLong())
        return pageResult(assemble(entries, offset), page, size, total)
    }

    private fun periodRanking(
        period: RankingPeriod,
        periodKey: String,
        page: Int,
        size: Int,
    ): PageResult<RankedProductResult> {
        val total = periodRankingRepository.size(period, periodKey)
        val entries = periodRankingRepository.topN(period, periodKey, page, size)
        // MV 의 rank_no 는 1부터 빈틈없이 적재되므로 offset 기반 rank 계산이 rank_no 와 일치한다.
        return pageResult(assemble(entries, offset = page.toLong() * size), page, size, total)
    }

    private fun pageResult(
        content: List<RankedProductResult>,
        page: Int,
        size: Int,
        total: Long,
    ): PageResult<RankedProductResult> {
        val totalPages = if (size == 0) 0 else ((total + size - 1) / size).toInt()
        return PageResult(content, page, size, total, totalPages)
    }

    private fun assemble(entries: List<RankedEntry>, offset: Long): List<RankedProductResult> {
        if (entries.isEmpty()) return emptyList()
        val products = productRepository.findAllByIds(entries.map { it.productId }).associateBy { it.id }
        val brands = brandRepository.findAllByIds(products.values.map { it.brandId }.distinct()).associateBy { it.id }

        return entries.mapIndexedNotNull { index, entry ->
            val product = products[entry.productId] ?: return@mapIndexedNotNull null
            val brand = brands[product.brandId] ?: return@mapIndexedNotNull null
            RankedProductResult(
                productId = product.id,
                name = product.name.value,
                price = product.price.value,
                brandName = brand.name.value,
                likeCount = product.likeCount,
                rank = offset + index + 1,
                score = entry.score,
            )
        }
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
