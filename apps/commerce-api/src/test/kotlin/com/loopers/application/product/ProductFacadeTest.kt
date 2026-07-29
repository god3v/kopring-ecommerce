package com.loopers.application.product

import com.loopers.domain.brand.BrandRepository
import com.loopers.domain.like.LikeRepository
import com.loopers.application.product.command.RegisterProductCommand
import com.loopers.application.product.command.UpdateProductCommand
import com.loopers.application.product.port.CachedProductDetail
import com.loopers.application.product.port.ProductCache
import com.loopers.application.product.query.GetProductsQuery
import com.loopers.application.product.result.ProductSummaryResult
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.product.ProductEvent
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.brand.BrandErrorType
import com.loopers.domain.brand.BrandFixture
import com.loopers.domain.product.Product
import com.loopers.domain.product.ProductErrorType
import com.loopers.domain.product.ProductFixture
import com.loopers.domain.product.ProductSortType
import com.loopers.domain.product.SalesStatus
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
import org.springframework.data.redis.RedisConnectionFailureException

@DisplayName("ProductFacade")
class ProductFacadeTest {
    private val productRepository: ProductRepository = mockk()
    private val brandRepository: BrandRepository = mockk()
    private val likeRepository: LikeRepository = mockk()

    // relaxed: put/evict(Unit) 은 no-op. get 은 기본 null(miss) 로 명시해 DB 경로로 폴백시킨다.
    private val productCache: ProductCache = mockk(relaxed = true)
    private val eventPublisher: DomainEventPublisher = mockk(relaxed = true)

    // relaxed: rankOf 는 기본 null(순위 없음) 로 폴백. 순위가 필요한 테스트만 명시 stub 한다.
    private val rankingRepository: com.loopers.domain.ranking.RankingRepository = mockk(relaxed = true)
    private val productFacade =
        ProductFacade(productRepository, brandRepository, likeRepository, productCache, eventPublisher, rankingRepository)

    init {
        every { productCache.getDetail(any()) } returns null
        every { productCache.getList(any(), any(), any(), any()) } returns null
    }

    private fun pageOf(
        vararg products: Product,
        page: Int = 0,
        size: Int = 20,
    ): PageResult<Product> = PageResult(
        content = products.toList(),
        page = page,
        size = size,
        totalElements = products.size.toLong(),
        totalPages = if (products.isEmpty()) 0 else 1,
    )

    private fun emptyPage(page: Int = 0, size: Int = 20): PageResult<Product> = pageOf(page = page, size = size)

    private fun productsQuery(
        sort: ProductSortType? = ProductSortType.LATEST,
        brandId: Long? = null,
        page: Int = 0,
        size: Int = 20,
    ): GetProductsQuery = GetProductsQuery(sort = sort, brandId = brandId, paging = PageQuery(page, size))

    @Nested
    @DisplayName("getProducts — UC-1 회원 카탈로그 목록")
    inner class GetProducts {
        @Test
        @DisplayName("sort=LATEST 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesLatestSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 0, 20)
            } returns pageOf(ProductFixture.validProduct())

            val result = productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST))

            assertThat(result.content).hasSize(1)
            verify { productRepository.findAll(ProductSortType.LATEST, null, 0, 20) }
        }

        @Test
        @DisplayName("sort=PRICE_ASC 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesPriceAscSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 20)
            } returns emptyPage()

            productFacade.getProducts(productsQuery(sort = ProductSortType.PRICE_ASC))

            verify { productRepository.findAll(ProductSortType.PRICE_ASC, null, 0, 20) }
        }

        @Test
        @DisplayName("sort=LIKES_DESC 로 조회하면 Repository 에 동일한 sort 가 전달된다")
        fun delegatesLikesDescSortToRepository() {
            every {
                productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 20)
            } returns emptyPage()

            productFacade.getProducts(productsQuery(sort = ProductSortType.LIKES_DESC))

            verify { productRepository.findAll(ProductSortType.LIKES_DESC, null, 0, 20) }
        }

        @Test
        @DisplayName("sort 가 null 이면 LATEST 로 해석되어 Repository 에 전달된다")
        fun nullSortResolvesToLatest() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 0, 20)
            } returns emptyPage()

            productFacade.getProducts(productsQuery(sort = null))

            verify { productRepository.findAll(ProductSortType.LATEST, null, 0, 20) }
        }

        @Test
        @DisplayName("brandId 필터를 지정하면 Repository 에 동일한 brandId 가 전달된다")
        fun delegatesBrandIdFilterToRepository() {
            val brandId = 42L
            every {
                productRepository.findAll(ProductSortType.LATEST, brandId, 0, 20)
            } returns emptyPage()

            productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST, brandId = brandId))

            verify { productRepository.findAll(ProductSortType.LATEST, brandId, 0, 20) }
        }

        @Test
        @DisplayName("page / size 가 Repository 에 그대로 전달된다")
        fun delegatesPagingToRepository() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 3, 50)
            } returns emptyPage(page = 3, size = 50)

            productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST, page = 3, size = 50))

            verify { productRepository.findAll(ProductSortType.LATEST, null, 3, 50) }
        }

        @Test
        @DisplayName("Repository 의 페이지 메타(page/size/totalElements/totalPages)가 결과로 전파된다")
        fun propagatesPageMeta() {
            every {
                productRepository.findAll(ProductSortType.LATEST, null, 0, 20)
            } returns PageResult(
                content = listOf(ProductFixture.validProduct()),
                page = 0,
                size = 20,
                totalElements = 57L,
                totalPages = 3,
            )

            val result = productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST))

            assertThat(result.page).isEqualTo(0)
            assertThat(result.size).isEqualTo(20)
            assertThat(result.totalElements).isEqualTo(57L)
            assertThat(result.totalPages).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("getProductDetail — UC-2 회원 상세")
    inner class GetProductDetail {
        @Test
        @DisplayName("존재하는 productId 로 호출하면 ProductDetailResult 가 반환된다")
        fun returnsProductDetailWithBrand() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            val result = productFacade.getProductDetail(productId = 1L, userId = null)

            assertThat(result.name).isEqualTo(product.name.value)
            assertThat(result.price).isEqualTo(product.price.value)
            assertThat(result.brandName).isEqualTo(brand.name.value)
        }

        @Test
        @DisplayName("상세 조회 시 오늘 랭킹판 순위가 함께 반환된다")
        fun includesTodayRank() {
            val product = ProductFixture.validProduct(id = 1L)
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { rankingRepository.rankOf(any(), 1L) } returns 3L

            val result = productFacade.getProductDetail(productId = 1L, userId = null)

            assertThat(result.rank).isEqualTo(3L)
        }

        @Test
        @DisplayName("랭킹판에 없는 상품의 상세 순위는 null 이다")
        fun rankIsNullWhenNotRanked() {
            val product = ProductFixture.validProduct(id = 1L)
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { rankingRepository.rankOf(any(), 1L) } returns null

            val result = productFacade.getProductDetail(productId = 1L, userId = null)

            assertThat(result.rank).isNull()
        }

        @Test
        @DisplayName("순위 조회의 저장소 접근 예외는 폴백된다 — 상세는 정상 반환되고 순위만 null 이다")
        fun rankFallsBackToNullOnInfraFailure() {
            val product = ProductFixture.validProduct(id = 1L)
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { rankingRepository.rankOf(any(), 1L) } throws RedisConnectionFailureException("redis down")

            val result = productFacade.getProductDetail(productId = 1L, userId = null)

            assertThat(result.rank).isNull()
            assertThat(result.name).isEqualTo(product.name.value)
        }

        @Test
        @DisplayName("순위 조회 중 인프라 외 런타임 오류는 전파된다 — 코드 결함을 성공 응답으로 가리지 않는다")
        fun rankUnexpectedErrorPropagates() {
            val product = ProductFixture.validProduct(id = 1L)
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { rankingRepository.rankOf(any(), 1L) } throws IllegalStateException("어댑터 결함")

            assertThrows<IllegalStateException> {
                productFacade.getProductDetail(productId = 1L, userId = null)
            }
        }

        @Test
        @DisplayName("상세 조회 시 ProductEvent.Viewed(productId·userId) 를 발행한다")
        fun publishesProductViewedEvent() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns false

            productFacade.getProductDetail(productId = 1L, userId = 7L)

            verify { eventPublisher.publish(match { it is ProductEvent.Viewed && it.productId == 1L && it.userId == 7L }) }
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생하고 조회 이벤트를 발행하지 않는다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> {
                productFacade.getProductDetail(productId = 99L, userId = null)
            }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("product 는 존재하나 brand 가 없으면 BRAND_NOT_FOUND 예외가 발생한다 (정합성)")
        fun throwsWhenBrandMissing() {
            val product = ProductFixture.validProduct()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns null

            val ex = assertThrows<CoreException> {
                productFacade.getProductDetail(productId = 1L, userId = null)
            }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
        }

        @Test
        @DisplayName("userId 가 null 이면 likedByMe=false 로 응답한다 (LikeRepository 호출 없음)")
        fun likedByMeFalseWhenAnonymous() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            val result = productFacade.getProductDetail(productId = 1L, userId = null)

            assertThat(result.likedByMe).isFalse()
            verify(exactly = 0) { likeRepository.existsByUserIdAndProductId(any(), any()) }
        }

        @Test
        @DisplayName("userId 가 주어지면 LikeRepository 결과를 likedByMe 로 응답한다")
        fun likedByMeFromLikeRepository() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns true

            val result = productFacade.getProductDetail(productId = 1L, userId = 7L)

            assertThat(result.likedByMe).isTrue()
        }
    }

    @Nested
    @DisplayName("getProductsForAdmin — UC-3 관리자 목록")
    inner class GetProductsForAdmin {
        @Test
        @DisplayName("Repository 의 findAllForAdmin 으로 최신순 페이징을 위임한다")
        fun delegatesToFindAllForAdmin() {
            every { productRepository.findAllForAdmin(null, 0, 20) } returns emptyPage()

            productFacade.getProductsForAdmin(brandId = null, pageQuery = PageQuery(0, 20))

            verify { productRepository.findAllForAdmin(null, 0, 20) }
        }

        @Test
        @DisplayName("brandId 필터를 Repository 에 전달한다")
        fun delegatesBrandIdFilter() {
            every { productRepository.findAllForAdmin(7L, 1, 30) } returns emptyPage(page = 1, size = 30)

            productFacade.getProductsForAdmin(brandId = 7L, pageQuery = PageQuery(1, 30))

            verify { productRepository.findAllForAdmin(7L, 1, 30) }
        }

        @Test
        @DisplayName("응답 항목에 salesStatus 가 포함된다")
        fun responseIncludesSalesStatus() {
            val product = ProductFixture.validProduct()
            every { productRepository.findAllForAdmin(null, 0, 20) } returns pageOf(product)

            val result = productFacade.getProductsForAdmin(brandId = null, pageQuery = PageQuery(0, 20))

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].salesStatus).isEqualTo(SalesStatus.ON_SALE)
        }

        @Test
        @DisplayName("Repository 의 페이지 메타가 결과로 전파된다")
        fun propagatesPageMeta() {
            every { productRepository.findAllForAdmin(null, 0, 20) } returns PageResult(
                content = listOf(ProductFixture.validProduct()),
                page = 0,
                size = 20,
                totalElements = 42L,
                totalPages = 3,
            )

            val result = productFacade.getProductsForAdmin(brandId = null, pageQuery = PageQuery(0, 20))

            assertThat(result.totalElements).isEqualTo(42L)
            assertThat(result.totalPages).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("getProductForAdmin — UC-10 관리자 상세")
    inner class GetProductForAdmin {
        @Test
        @DisplayName("존재하는 productId 로 호출하면 AdminProductDetailResult 가 반환된다")
        fun returnsAdminProductDetail() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            val result = productFacade.getProductForAdmin(productId = 1L)

            assertThat(result.brandName).isEqualTo(brand.name.value)
            assertThat(result.salesStatus).isEqualTo(SalesStatus.ON_SALE)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.getProductForAdmin(99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("registerProduct — UC-11 관리자 등록")
    inner class RegisterProduct {
        private val command = RegisterProductCommand(
            brandId = 1L,
            name = "갤럭시 S25",
            price = 1_300_000,
            stock = 50,
        )

        @Test
        @DisplayName("유효한 입력으로 등록하면 salesStatus=ON_SALE 의 신규 Product 가 저장된다")
        fun registersProductWithOnSale() {
            val brand = BrandFixture.validBrand()
            val saved = slot<Product>()
            every { brandRepository.findById(1L) } returns brand
            every { productRepository.existsByBrandIdAndName(1L, command.name) } returns false
            every { productRepository.save(capture(saved)) } answers { saved.captured }

            productFacade.registerProduct(command)

            assertThat(saved.captured.name.value).isEqualTo(command.name)
            assertThat(saved.captured.price.value).isEqualTo(command.price)
            assertThat(saved.captured.brandId).isEqualTo(command.brandId)
            assertThat(saved.captured.salesStatus).isEqualTo(SalesStatus.ON_SALE)
            verify { productRepository.save(any()) }
        }

        @Test
        @DisplayName("지정 brandId 가 존재하지 않으면 BRAND_NOT_FOUND 예외가 발생한다")
        fun throwsWhenBrandMissing() {
            every { brandRepository.findById(1L) } returns null

            val ex = assertThrows<CoreException> { productFacade.registerProduct(command) }
            assertThat(ex.errorType).isEqualTo(BrandErrorType.BRAND_NOT_FOUND)
            verify(exactly = 0) { productRepository.save(any()) }
        }

        @Test
        @DisplayName("같은 브랜드 안에 같은 이름이 이미 있으면 DUPLICATE_PRODUCT_NAME 예외가 발생한다")
        fun throwsWhenDuplicateName() {
            every { brandRepository.findById(1L) } returns BrandFixture.validBrand()
            every { productRepository.existsByBrandIdAndName(1L, command.name) } returns true

            val ex = assertThrows<CoreException> { productFacade.registerProduct(command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.DUPLICATE_PRODUCT_NAME)
            verify(exactly = 0) { productRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateProduct — UC-12 관리자 수정")
    inner class UpdateProduct {
        @Test
        @DisplayName("정상 입력으로 수정하면 name / price / salesStatus 가 갱신된다")
        fun updatesNamePriceSalesStatus() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = "맥북 프로 16인치",
                price = 3_300_000,
                salesStatus = SalesStatus.OFF_SALE,
            )
            every { productRepository.findById(1L) } returns product
            every { productRepository.existsByBrandIdAndName(product.brandId, command.name) } returns false
            every { brandRepository.findById(product.brandId) } returns BrandFixture.validBrand()
            every { productRepository.save(product) } returns product

            productFacade.updateProduct(productId = 1L, command = command)

            assertThat(product.name.value).isEqualTo(command.name)
            assertThat(product.price.value).isEqualTo(command.price)
            assertThat(product.salesStatus).isEqualTo(SalesStatus.OFF_SALE)
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            val command = UpdateProductCommand("x", 1, SalesStatus.ON_SALE)
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.updateProduct(99L, command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
        }

        @Test
        @DisplayName("같은 브랜드에 같은 이름의 다른 상품이 있으면 DUPLICATE_PRODUCT_NAME 예외가 발생한다 (자기 자신 제외)")
        fun throwsWhenAnotherWithSameName() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = "갤럭시 S25",
                price = 1_300_000,
                salesStatus = SalesStatus.ON_SALE,
            )
            every { productRepository.findById(1L) } returns product
            every { productRepository.existsByBrandIdAndName(product.brandId, command.name) } returns true

            val ex = assertThrows<CoreException> { productFacade.updateProduct(1L, command) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.DUPLICATE_PRODUCT_NAME)
        }

        @Test
        @DisplayName("이름이 동일하면 (자기 자신) 중복 검사를 호출하지 않는다")
        fun skipsDuplicateCheckWhenSameName() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = product.name.value,
                price = 999,
                salesStatus = SalesStatus.OUT_OF_STOCK,
            )
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns BrandFixture.validBrand()
            every { productRepository.save(product) } returns product

            productFacade.updateProduct(1L, command)

            verify(exactly = 0) { productRepository.existsByBrandIdAndName(any(), any()) }
        }
    }

    @Nested
    @DisplayName("deleteProduct — UC-13 관리자 삭제")
    inner class DeleteProduct {
        @Test
        @DisplayName("정상 호출 시 Product 가 soft delete 된 채로 저장된다")
        fun softDeletesProduct() {
            val product = ProductFixture.validProduct()
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns product

            productFacade.deleteProduct(productId = 1L)

            assertThat(product.isDeleted()).isTrue()
            verify { productRepository.save(product) }
        }

        @Test
        @DisplayName("삭제 시 PRODUCT_DELETED 이벤트를 발행한다 — 랭킹판 정리를 위한 외부 전파")
        fun publishesDeletedEvent() {
            val product = ProductFixture.validProduct(id = 1L)
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns product

            productFacade.deleteProduct(productId = 1L)

            verify { eventPublisher.publish(match { it is ProductEvent.Deleted && it.productId == 1L }) }
        }

        @Test
        @DisplayName("존재하지 않거나 이미 삭제된 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { productFacade.deleteProduct(99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { productRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("캐시 read-through / 무효화")
    inner class Caching {
        @Test
        @DisplayName("상세 캐시 히트 시 DB 를 조회하지 않고 캐시 본문 + 합성한 likedByMe 로 응답한다")
        fun detailCacheHitSkipsDb() {
            val cached = CachedProductDetail(
                id = 1L,
                name = "운동화",
                price = 59_000L,
                likeCount = 3L,
                brandId = 9L,
                brandName = "나이키",
            )
            every { productCache.getDetail(1L) } returns cached
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns true

            val result = productFacade.getProductDetail(productId = 1L, userId = 7L)

            assertThat(result.name).isEqualTo("운동화")
            assertThat(result.brandName).isEqualTo("나이키")
            assertThat(result.likedByMe).isTrue()
            verify(exactly = 0) { productRepository.findById(any()) }
            verify(exactly = 0) { brandRepository.findById(any()) }
        }

        @Test
        @DisplayName("상세 캐시 미스 시 DB 로 적재하고 캐시에 put 한다")
        fun detailCacheMissLoadsAndPuts() {
            val product = ProductFixture.validProduct()
            val brand = BrandFixture.validBrand()
            every { productCache.getDetail(1L) } returns null
            every { productRepository.findById(1L) } returns product
            every { brandRepository.findById(product.brandId) } returns brand

            productFacade.getProductDetail(productId = 1L, userId = null)

            verify { productCache.putDetail(any()) }
        }

        @Test
        @DisplayName("목록 캐시 히트 시 DB 를 조회하지 않는다")
        fun listCacheHitSkipsDb() {
            val cachedPage = PageResult(
                content = listOf(ProductSummaryResult(1L, "운동화", 59_000L, 3L, 9L)),
                page = 0,
                size = 20,
                totalElements = 1L,
                totalPages = 1,
            )
            every { productCache.getList(null, ProductSortType.LATEST, 0, 20) } returns cachedPage

            val result = productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST))

            assertThat(result.content.map { it.name }).containsExactly("운동화")
            verify(exactly = 0) { productRepository.findAll(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("캐시 상한(page>=5) 페이지는 캐시를 조회/저장하지 않고 DB 를 조회한다")
        fun deepPageSkipsCache() {
            every { productRepository.findAll(ProductSortType.LATEST, null, 5, 20) } returns emptyPage(page = 5)

            productFacade.getProducts(productsQuery(sort = ProductSortType.LATEST, page = 5))

            verify(exactly = 0) { productCache.getList(any(), any(), any(), any()) }
            verify(exactly = 0) { productCache.putList(any(), any(), any(), any(), any()) }
            verify { productRepository.findAll(ProductSortType.LATEST, null, 5, 20) }
        }

        @Test
        @DisplayName("상품 수정 시 상세 캐시를 무효화한다")
        fun updateEvictsDetail() {
            val product = ProductFixture.validProduct()
            val command = UpdateProductCommand(
                name = "맥북 프로 16인치",
                price = 3_300_000,
                salesStatus = SalesStatus.OFF_SALE,
            )
            every { productRepository.findById(1L) } returns product
            every { productRepository.existsByBrandIdAndName(product.brandId, command.name) } returns false
            every { brandRepository.findById(product.brandId) } returns BrandFixture.validBrand()
            every { productRepository.save(product) } returns product

            productFacade.updateProduct(productId = 1L, command = command)

            verify { productCache.evictDetail(1L) }
        }

        @Test
        @DisplayName("상품 삭제 시 상세 캐시를 무효화한다")
        fun deleteEvictsDetail() {
            val product = ProductFixture.validProduct()
            every { productRepository.findById(1L) } returns product
            every { productRepository.save(product) } returns product

            productFacade.deleteProduct(productId = 1L)

            verify { productCache.evictDetail(1L) }
        }
    }
}
