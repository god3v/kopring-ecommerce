package com.loopers.infrastructure.brand

import com.loopers.domain.brand.BrandRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.brand.BrandErrorType
import com.loopers.domain.brand.BrandFixture
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
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, BrandRepositoryImpl::class)
class BrandRepositoryImplIntegrationTest @Autowired constructor(
    private val brandRepository: BrandRepository,
    private val brandJpaRepository: BrandJpaRepository,
    private val testEntityManager: TestEntityManager,
) {
    @DisplayName("save / findById 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findById 로 동일 도메인 객체가 복원된다.")
        @Test
        fun saveThenFindById() {
            val saved = brandRepository.save(BrandFixture.validBrand("애플"))
            testEntityManager.flush()
            testEntityManager.clear()

            val found = brandRepository.findById(saved.id)

            assertThat(found).isNotNull()
            val nonNullFound = requireNotNull(found)
            assertThat(nonNullFound.id).isEqualTo(saved.id)
            assertThat(nonNullFound.name.value).isEqualTo("애플")
        }

        @DisplayName("findById 는 soft-deleted Brand 를 null 로 반환한다.")
        @Test
        fun findByIdExcludesSoftDeleted() {
            val saved = brandRepository.save(BrandFixture.validBrand())
            testEntityManager.flush()
            saved.softDelete()
            brandRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            val found = brandRepository.findById(saved.id)

            assertThat(found).isNull()
        }

        @DisplayName("DB 에 존재하지 않는 id 로 save(update) 하면, BRAND_NOT_FOUND 예외가 발생한다.")
        @Test
        fun throwsBrandNotFound_whenUpdatingNonExistentId() {
            val ghost = BrandFixture.validBrand(id = 999L)

            val ex = assertThrows<CoreException> { brandRepository.save(ghost) }

            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }
    }

    @DisplayName("findAll(page, size)")
    @Nested
    inner class FindAll {
        @DisplayName("createdAt DESC, id DESC 정렬 + soft-deleted 제외 + 페이징이 적용된다.")
        @Test
        fun findAllOrdersAndExcludesAndPagesIn() {
            brandRepository.save(BrandFixture.validBrand("A"))
            brandRepository.save(BrandFixture.validBrand("B"))
            val third = brandRepository.save(BrandFixture.validBrand("C"))
            testEntityManager.flush()
            third.softDelete()
            brandRepository.save(third)
            testEntityManager.flush()
            testEntityManager.clear()

            val page0 = brandRepository.findAll(page = 0, size = 1)
            val page1 = brandRepository.findAll(page = 1, size = 1)

            assertThat(page0.content.map { it.name.value }).containsExactly("B")
            assertThat(page1.content.map { it.name.value }).containsExactly("A")
            assertThat(brandRepository.findAll(0, 10).content.map { it.name.value })
                .doesNotContain("C")
            // soft-deleted 제외 후 A·B 2건 → size 1 기준 totalElements=2 / totalPages=2.
            assertThat(page0.totalElements).isEqualTo(2L)
            assertThat(page0.totalPages).isEqualTo(2)
            assertThat(page0.page).isEqualTo(0)
            assertThat(page0.size).isEqualTo(1)
        }

        @DisplayName("sleep 없이 동일 시점 다건을 저장해도 페이지 순서가 항상 동일하다 (tie-breaker 안정성).")
        @Test
        fun stableOrderAcrossPages_withoutSleep() {
            val names = listOf("A", "B", "C", "D", "E")
            names.forEach { brandRepository.save(BrandFixture.validBrand(it)) }
            testEntityManager.flush()
            testEntityManager.clear()

            // 나중에 저장될수록 큰 id → createdAt DESC, id DESC 로 역순 정렬이 결정적이다.
            val expected = names.reversed()

            fun fullOrderPagedBy(size: Int): List<String> =
                names.indices.flatMap { page ->
                    brandRepository.findAll(page = page, size = size).content.map { it.name.value }
                }

            // 페이지 크기를 달리해도(1, 2) 합친 순서가 전체 순서와 동일 → 중복/누락 없음.
            assertThat(brandRepository.findAll(0, names.size).content.map { it.name.value })
                .containsExactlyElementsOf(expected)
            assertThat(fullOrderPagedBy(size = 1)).containsExactlyElementsOf(expected)
            assertThat(fullOrderPagedBy(size = 2)).containsExactlyElementsOf(expected)
        }
    }

    @DisplayName("existsByName")
    @Nested
    inner class ExistsByName {
        @DisplayName("soft-deleted 를 제외하고 판정한다.")
        @Test
        fun existsByNameExcludesSoftDeleted() {
            val saved = brandRepository.save(BrandFixture.validBrand("애플"))
            testEntityManager.flush()
            assertThat(brandRepository.existsByName("애플")).isTrue()

            saved.softDelete()
            brandRepository.save(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(brandRepository.existsByName("애플")).isFalse()
        }
    }
}
