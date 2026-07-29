package com.loopers.application.brand

import com.loopers.application.brand.command.RegisterBrandCommand
import com.loopers.application.brand.command.UpdateBrandCommand
import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.brand.Brand
import com.loopers.domain.brand.BrandErrorType
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.product.ProductFixture
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("BrandFacade")
class BrandFacadeTest {
    private val brandRepository: BrandRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val brandFacade = BrandFacade(brandRepository, productRepository)

    @Nested
    @DisplayName("getBrand — UC-3 회원 단일 브랜드")
    inner class GetBrand {
        @Test
        @DisplayName("존재하는 brandId 로 호출하면 BrandResult 가 반환된다")
        fun returnsBrandResult() {
            val brand = BrandFixture.validBrand()
            every { brandRepository.findById(1L) } returns brand

            val result = brandFacade.getBrand(1L)

            assertThat(result.name).isEqualTo(brand.name.value)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 brandId 면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { brandRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { brandFacade.getBrand(99L) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("getBrandsForAdmin — UC-4 관리자 목록")
    inner class GetBrandsForAdmin {
        @Test
        @DisplayName("Repository.findAll 로 위임되고 페이지 메타가 전파된다")
        fun delegatesToFindAll() {
            // content 1건이지만 전체 5건/3페이지 — Facade 가 메타를 재계산하지 않고 그대로 전파하는지 검증.
            every { brandRepository.findAll(0, 20) } returns
                PageResult(content = listOf(BrandFixture.validBrand()), page = 0, size = 20, totalElements = 5L, totalPages = 3)

            val result = brandFacade.getBrandsForAdmin(PageQuery(page = 0, size = 20))

            assertThat(result.content).hasSize(1)
            assertThat(result.totalElements).isEqualTo(5L)
            assertThat(result.totalPages).isEqualTo(3)
            verify { brandRepository.findAll(0, 20) }
        }

        @Test
        @DisplayName("page / size 가 Repository 에 그대로 전달된다")
        fun delegatesPaging() {
            every { brandRepository.findAll(2, 50) } returns
                PageResult(content = emptyList(), page = 2, size = 50, totalElements = 0L, totalPages = 0)

            brandFacade.getBrandsForAdmin(PageQuery(page = 2, size = 50))

            verify { brandRepository.findAll(2, 50) }
        }

        @Test
        @DisplayName("등록된 브랜드가 없으면 빈 content 가 반환된다")
        fun returnsEmptyWhenNone() {
            every { brandRepository.findAll(0, 20) } returns
                PageResult(content = emptyList(), page = 0, size = 20, totalElements = 0L, totalPages = 0)

            val result = brandFacade.getBrandsForAdmin(PageQuery(page = 0, size = 20))

            assertThat(result.content).isEmpty()
        }
    }

    @Nested
    @DisplayName("getBrandForAdmin — UC-5 관리자 상세")
    inner class GetBrandForAdmin {
        @Test
        @DisplayName("정상 응답으로 AdminBrandResult 가 반환된다")
        fun returnsAdminBrandResult() {
            val brand = BrandFixture.validBrand()
            every { brandRepository.findById(1L) } returns brand

            val result = brandFacade.getBrandForAdmin(1L)

            assertThat(result.name).isEqualTo(brand.name.value)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 brandId 면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { brandRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { brandFacade.getBrandForAdmin(99L) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("registerBrand — UC-6 관리자 등록")
    inner class RegisterBrand {
        private val command = RegisterBrandCommand(name = "삼성")

        @Test
        @DisplayName("유효한 입력으로 등록하면 신규 Brand 가 저장된다")
        fun registersBrand() {
            val saved = slot<Brand>()
            every { brandRepository.existsByName(command.name) } returns false
            every { brandRepository.save(capture(saved)) } answers { saved.captured }

            brandFacade.registerBrand(command)

            assertThat(saved.captured.name.value).isEqualTo(command.name)
            verify { brandRepository.save(any()) }
        }

        @Test
        @DisplayName("동일 이름의 브랜드가 이미 있으면 DUPLICATE_BRAND_NAME 예외가 발생한다")
        fun throwsWhenDuplicate() {
            every { brandRepository.existsByName(command.name) } returns true

            val ex = assertThrows<CoreException> { brandFacade.registerBrand(command) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.DUPLICATE_BRAND_NAME)
            verify(exactly = 0) { brandRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateBrand — UC-7 관리자 수정")
    inner class UpdateBrand {
        @Test
        @DisplayName("정상 입력으로 수정하면 name 이 갱신된다")
        fun updatesName() {
            val brand = BrandFixture.validBrand()
            val command = UpdateBrandCommand(name = "Apple Inc.")
            every { brandRepository.findById(1L) } returns brand
            every { brandRepository.existsByName(command.name) } returns false
            every { brandRepository.save(brand) } returns brand

            brandFacade.updateBrand(1L, command)

            assertThat(brand.name.value).isEqualTo(command.name)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 brandId 면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { brandRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> {
                brandFacade.updateBrand(99L, UpdateBrandCommand("x"))
            }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }

        @Test
        @DisplayName("다른 브랜드와 이름이 중복되면 DUPLICATE_BRAND_NAME 예외가 발생한다 (자기 자신 제외)")
        fun throwsWhenDuplicate() {
            val brand = BrandFixture.validBrand()
            val command = UpdateBrandCommand(name = "삼성")
            every { brandRepository.findById(1L) } returns brand
            every { brandRepository.existsByName(command.name) } returns true

            val ex = assertThrows<CoreException> { brandFacade.updateBrand(1L, command) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.DUPLICATE_BRAND_NAME)
        }

        @Test
        @DisplayName("이름이 동일하면 (자기 자신) 중복 검사를 호출하지 않는다")
        fun skipsDuplicateCheckWhenSameName() {
            val brand = BrandFixture.validBrand()
            val command = UpdateBrandCommand(name = brand.name.value)
            every { brandRepository.findById(1L) } returns brand
            every { brandRepository.save(brand) } returns brand

            brandFacade.updateBrand(1L, command)

            verify(exactly = 0) { brandRepository.existsByName(any()) }
        }
    }

    @Nested
    @DisplayName("deleteBrand — UC-8 카스케이드 삭제")
    inner class DeleteBrand {
        @Test
        @DisplayName("정상 호출 시 Brand 및 소속 Product 가 모두 soft delete 된다")
        fun cascadeSoftDeletesBrandAndProducts() {
            val brand = BrandFixture.validBrand()
            val productA = ProductFixture.validProduct(name = "P1")
            val productB = ProductFixture.validProduct(name = "P2")
            every { brandRepository.findById(1L) } returns brand
            every { productRepository.findAllByBrandId(1L) } returns listOf(productA, productB)
            every { productRepository.saveAll(any()) } answers { firstArg() }
            every { brandRepository.save(brand) } returns brand

            brandFacade.deleteBrand(1L)

            assertThat(brand.isDeleted()).isTrue()
            assertThat(productA.isDeleted()).isTrue()
            assertThat(productB.isDeleted()).isTrue()
            verify(exactly = 0) { productRepository.save(any()) }
            verify { productRepository.saveAll(listOf(productA, productB)) }
            verify { brandRepository.save(brand) }
        }

        @Test
        @DisplayName("존재하지 않거나 이미 삭제된 brandId 면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { brandRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { brandFacade.deleteBrand(99L) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
            verify(exactly = 0) { productRepository.findAllByBrandId(any()) }
            verify(exactly = 0) { brandRepository.save(any()) }
        }

        @Test
        @DisplayName("소속 Product 가 없으면 Brand 만 soft delete 된다")
        fun deletesOnlyBrandWhenNoProducts() {
            val brand = BrandFixture.validBrand()
            every { brandRepository.findById(1L) } returns brand
            every { productRepository.findAllByBrandId(1L) } returns emptyList()
            every { productRepository.saveAll(emptyList()) } returns emptyList()
            every { brandRepository.save(brand) } returns brand

            brandFacade.deleteBrand(1L)

            assertThat(brand.isDeleted()).isTrue()
            verify(exactly = 0) { productRepository.save(any()) }
        }
    }
}
