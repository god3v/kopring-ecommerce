package com.loopers.infrastructure.product

import com.loopers.domain.product.ProductRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductSortType
import com.loopers.support.error.CoreException
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, ProductRepositoryImpl::class)
class ProductRepositoryImplIntegrationTest @Autowired constructor(
    private val productRepository: ProductRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun persist(
        name: String = "P",
        price: Long = 1000L,
        likeCount: Long = 0L,
        brandId: Long = 1L,
    ): Product {
        val saved = productRepository.save(
            ProductFixture.validProduct(name = name, price = price, likeCount = likeCount, brandId = brandId),
        )
        testEntityManager.flush()
        return saved
    }

    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다.")
        @Test
        fun saveThenFindById() {
            val saved = persist(name = "M1", price = 1_000_000)
            testEntityManager.clear()

            val found = productRepository.findById(saved.id)

            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected Product but was null (id=${saved.id})" }
            assertThat(verifiedFound.name.value).isEqualTo("M1")
            assertThat(verifiedFound.price.value).isEqualTo(1_000_000)
        }

        @DisplayName("findById 는 soft-deleted Product 를 null 로 반환한다.")
        @Test
        fun findByIdExcludesSoftDeleted() {
            val saved = persist()
            saved.softDelete()
            productRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = productRepository.findById(saved.id)

            assertThat(found).isNull()
        }

        @DisplayName("DB 에 존재하지 않는 id 로 save(update) 하면, PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsProductNotFound_whenUpdatingNonExistentId() {
            val ghost = ProductFixture.validProduct(id = 999L)

            val ex = assertThrows<CoreException> { productRepository.save(ghost) }

            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }
    }

    @DisplayName("findAll(sort, brandId, page, size)")
    @Nested
    inner class FindAll {
        @DisplayName("sort=LATEST 는 createdAt desc 로 정렬한다.")
        @Test
        fun latestOrdersByCreatedAtDesc() {
            val a = persist(name = "A")
            Thread.sleep(10)
            val b = persist(name = "B")
            Thread.sleep(10)
            val c = persist(name = "C")
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, null, 0, 10)

            assertThat(result.content.map { it.id }).containsExactly(c.id, b.id, a.id)
            assertThat(result.totalElements).isEqualTo(3L)
            assertThat(result.totalPages).isEqualTo(1)
        }

        @DisplayName("sort=PRICE_ASC 는 price asc 로 정렬한다.")
        @Test
        fun priceAscOrdersByPriceAsc() {
            persist(name = "high", price = 9000)
            persist(name = "low", price = 1000)
            persist(name = "mid", price = 5000)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 10)

            assertThat(result.content.map { it.price.value }).containsExactly(1000, 5000, 9000)
        }

        @DisplayName("sort=LIKES_DESC 는 likeCount desc 로 정렬한다.")
        @Test
        fun likesDescOrdersByLikeCountDesc() {
            persist(name = "few", likeCount = 1)
            persist(name = "many", likeCount = 100)
            persist(name = "mid", likeCount = 30)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 10)

            assertThat(result.content.map { it.likeCount }).containsExactly(100L, 30L, 1L)
        }

        @DisplayName("sort=PRICE_ASC 동점 시 id asc 로 정렬한다. (타이브레이크 방향을 주 정렬과 통일 → 평범한 오름차순 인덱스로 filesort 회피)")
        @Test
        fun priceAscTiebreakIsIdAsc() {
            val first = persist(name = "p1", price = 5000)
            val second = persist(name = "p2", price = 5000)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 10)

            assertThat(result.content.map { it.id }).containsExactly(first.id, second.id)
        }

        @DisplayName("sort=LIKES_DESC 동점 시 id desc 로 정렬한다. (주 정렬이 desc 라 타이브레이크도 desc)")
        @Test
        fun likesDescTiebreakIsIdDesc() {
            val first = persist(name = "l1", likeCount = 10)
            val second = persist(name = "l2", likeCount = 10)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 10)

            assertThat(result.content.map { it.id }).containsExactly(second.id, first.id)
        }

        @DisplayName("soft-deleted Product 를 제외한다.")
        @Test
        fun excludesSoftDeleted() {
            persist(name = "live")
            val dead = persist(name = "dead")
            dead.softDelete()
            productRepository.save(dead)
            testEntityManager.flush()
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, null, 0, 10)

            assertThat(result.content.map { it.name.value }).containsExactly("live")
            // @SQLRestriction(deleted_at IS NULL) 이 count 쿼리에도 적용되어 삭제분이 totalElements 에서 제외된다.
            assertThat(result.totalElements).isEqualTo(1L)
        }

        @DisplayName("brandId 필터가 적용된다.")
        @Test
        fun appliesBrandIdFilter() {
            persist(name = "B1-1", brandId = 1L)
            persist(name = "B1-2", brandId = 1L)
            persist(name = "B2-1", brandId = 2L)
            testEntityManager.clear()

            val result = productRepository.findAll(ProductSortType.LATEST, 2L, 0, 10)

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].brandId).isEqualTo(2L)
            assertThat(result.totalElements).isEqualTo(1L)
        }

        @DisplayName("page / size 페이징이 적용된다.")
        @Test
        fun appliesPagination() {
            (1..5).forEach {
                persist(name = "P$it")
                Thread.sleep(5)
            }
            testEntityManager.clear()

            val page0 = productRepository.findAll(ProductSortType.LATEST, null, 0, 2)
            val page1 = productRepository.findAll(ProductSortType.LATEST, null, 1, 2)

            assertThat(page0.content).hasSize(2)
            assertThat(page1.content).hasSize(2)
            assertThat(page0.content[0].id).isNotEqualTo(page1.content[0].id)
            assertThat(page0.totalElements).isEqualTo(5L)
            assertThat(page0.totalPages).isEqualTo(3)
        }
    }

    @DisplayName("findAllForAdmin")
    @Nested
    inner class FindAllForAdmin {
        @DisplayName("createdAt desc + soft delete 제외 + brandId 필터를 적용한다.")
        @Test
        fun adminListAppliesAllRules() {
            val a = persist(name = "A", brandId = 1L)
            Thread.sleep(10)
            val b = persist(name = "B", brandId = 1L)
            Thread.sleep(10)
            val c = persist(name = "C", brandId = 2L)
            b.softDelete()
            productRepository.save(b)
            testEntityManager.flush()
            testEntityManager.clear()

            val all = productRepository.findAllForAdmin(null, 0, 10)
            val brandOne = productRepository.findAllForAdmin(1L, 0, 10)

            assertThat(all.content.map { it.id }).containsExactly(c.id, a.id)
            assertThat(all.totalElements).isEqualTo(2L)
            assertThat(brandOne.content.map { it.id }).containsExactly(a.id)
            assertThat(brandOne.totalElements).isEqualTo(1L)
        }
    }

    @DisplayName("existsByBrandIdAndName")
    @Nested
    inner class ExistsByBrandIdAndName {
        @DisplayName("soft-deleted 를 제외하고 판정한다.")
        @Test
        fun existsExcludesSoftDeleted() {
            val saved = persist(name = "X", brandId = 1L)
            assertThat(productRepository.existsByBrandIdAndName(1L, "X")).isTrue()

            saved.softDelete()
            productRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(productRepository.existsByBrandIdAndName(1L, "X")).isFalse()
        }
    }

    @DisplayName("findAllByBrandId")
    @Nested
    inner class FindAllByBrandId {
        @DisplayName("해당 브랜드의 (soft-deleted 제외) 상품을 모두 반환한다.")
        @Test
        fun returnsLiveProductsOfBrand() {
            val a = persist(name = "B1-A", brandId = 1L)
            val b = persist(name = "B1-B", brandId = 1L)
            val c = persist(name = "B2-C", brandId = 2L)
            b.softDelete()
            productRepository.save(b)
            testEntityManager.flush()
            testEntityManager.clear()

            val result = productRepository.findAllByBrandId(1L)

            assertThat(result.map { it.id }).containsExactly(a.id)
        }
    }

    @DisplayName("saveAll — 배치 저장")
    @Nested
    inner class SaveAll {
        @DisplayName("여러 기존 Product 의 변경(soft delete) 을 한 번에 반영한다.")
        @Test
        fun batchUpdatesExistingProducts() {
            val a = persist(name = "A")
            val b = persist(name = "B")
            testEntityManager.clear()
            a.softDelete()
            b.softDelete()

            productRepository.saveAll(listOf(a, b))
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(productRepository.findById(a.id)).isNull()
            assertThat(productRepository.findById(b.id)).isNull()
        }

        @DisplayName("신규(id=0) Product 들을 insert 하고 식별자가 부여된 도메인을 반환한다.")
        @Test
        fun batchInsertsNewProducts() {
            val news = listOf(
                ProductFixture.validProduct(name = "N1"),
                ProductFixture.validProduct(name = "N2"),
            )

            val result = productRepository.saveAll(news)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).allMatch { it > 0L }
            assertThat(productRepository.findById(result.first().id)).isNotNull()
        }

        @DisplayName("존재하지 않는 id 가 섞이면 PRODUCT_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsProductNotFound_whenAnyIdMissing() {
            val live = persist(name = "L")
            testEntityManager.clear()
            val ghost = ProductFixture.validProduct(id = 999L)

            val ex = assertThrows<CoreException> { productRepository.saveAll(listOf(live, ghost)) }

            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }

        @DisplayName("빈 컬렉션이면 조회 없이 빈 리스트를 반환한다.")
        @Test
        fun returnsEmptyForEmptyInput() {
            assertThat(productRepository.saveAll(emptyList())).isEmpty()
        }
    }
}
