package com.loopers.application.like

import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeRepository
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeErrorType
import com.loopers.domain.product.ProductErrorType
import com.loopers.application.like.query.GetMyLikesQuery
import com.loopers.domain.product.ProductFixture
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("LikeFacade")
class LikeFacadeTest {
    private val likeRepository: LikeRepository = mockk()
    private val productRepository: ProductRepository = mockk()
    private val eventPublisher: DomainEventPublisher = mockk(relaxed = true)
    private val likeFacade = LikeFacade(likeRepository, productRepository, eventPublisher)

    @Nested
    @DisplayName("like — UC-1 멱등 등록")
    inner class LikeRegister {
        @Test
        @DisplayName("신규 호출 시 Like 가 저장되고 LikeChangedEvent(+1) 를 발행한다")
        fun newLikePublishesLikeChangedEvent() {
            val product = ProductFixture.validProduct(likeCount = 3L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns false
            every { likeRepository.save(any()) } answers { firstArg() }

            likeFacade.like(userId = 7L, productId = 1L)

            // 좋아요 수 집계는 직접 UPDATE 가 아니라 이벤트로 위임한다 — 커밋 이후 리스너가 원자 증감으로 반영.
            verify { likeRepository.save(any()) }
            verify { eventPublisher.publish(match { it is LikeEvent.Created && it.productId == 1L }) }
        }

        @Test
        @DisplayName("이미 좋아요한 상태에서 재호출하면 추가 저장·발행 없이 멱등 통과한다")
        fun idempotentWhenAlreadyLiked() {
            val product = ProductFixture.validProduct(likeCount = 3L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.existsByUserIdAndProductId(7L, 1L) } returns true

            likeFacade.like(userId = 7L, productId = 1L)

            verify(exactly = 0) { likeRepository.save(any()) }
            verify(exactly = 0) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { likeFacade.like(userId = 7L, productId = 99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { likeRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("unlike — UC-2 멱등 취소")
    inner class Unlike {
        @Test
        @DisplayName("좋아요 행이 실제로 제거되면(1건) LikeChangedEvent(-1) 를 발행한다")
        fun removesLikeAndPublishesLikeChangedEvent() {
            val product = ProductFixture.validProduct(likeCount = 5L)
            val existing = Like.create(userId = 7L, productId = 1L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.findByUserIdAndProductId(7L, 1L) } returns existing
            every { likeRepository.delete(existing) } returns 1L

            likeFacade.unlike(userId = 7L, productId = 1L)

            verify { likeRepository.delete(existing) }
            verify { eventPublisher.publish(match { it is LikeEvent.Canceled && it.productId == 1L }) }
        }

        @Test
        @DisplayName("삭제된 행이 0건이면(동시 중복 취소 패자) 이벤트를 발행하지 않는다")
        fun skipsPublishWhenNothingDeleted() {
            val product = ProductFixture.validProduct(likeCount = 5L)
            val existing = Like.create(userId = 7L, productId = 1L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.findByUserIdAndProductId(7L, 1L) } returns existing
            // 다른 동시 요청이 먼저 삭제 → 이 요청의 delete 는 0건. 발행 게이트가 닫혀 카운트 drift 를 막는다.
            every { likeRepository.delete(existing) } returns 0L

            likeFacade.unlike(userId = 7L, productId = 1L)

            verify { likeRepository.delete(existing) }
            verify(exactly = 0) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("좋아요가 없는 상태에서 호출하면 무동작 멱등 통과한다")
        fun idempotentWhenNotLiked() {
            val product = ProductFixture.validProduct(likeCount = 5L)
            every { productRepository.findById(1L) } returns product
            every { likeRepository.findByUserIdAndProductId(7L, 1L) } returns null

            likeFacade.unlike(userId = 7L, productId = 1L)

            verify(exactly = 0) { likeRepository.delete(any()) }
            verify(exactly = 0) { eventPublisher.publish(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 productId 면 PRODUCT_NOT_FOUND 예외가 발생한다")
        fun throwsWhenProductMissing() {
            every { productRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { likeFacade.unlike(userId = 7L, productId = 99L) }
            assertThat(ex.errorType).isEqualTo(ProductErrorType.PRODUCT_NOT_FOUND)
            verify(exactly = 0) { likeRepository.delete(any()) }
        }
    }

    @Nested
    @DisplayName("getMyLikes — UC-3 내 목록")
    inner class GetMyLikes {
        @Test
        @DisplayName("본인 식별자로 호출하면 좋아요한 상품 요약 목록이 반환되고 페이지 메타가 그대로 전파된다")
        fun returnsLikedProducts() {
            val product1 = ProductFixture.validProduct(id = 1L, name = "A")
            val product2 = ProductFixture.validProduct(id = 2L, name = "B")
            val like1 = Like.create(userId = 7L, productId = 1L)
            val like2 = Like.create(userId = 7L, productId = 2L)
            // content 2건이지만 전체 5건/3페이지인 0번째 페이지 — Facade 가 메타를 재계산하지 않고 포트 값을 전파하는지 검증.
            every { likeRepository.findAllByUserId(7L, 0, 20) } returns
                PageResult(content = listOf(like1, like2), page = 0, size = 20, totalElements = 5L, totalPages = 3)
            every { productRepository.findAllByIds(listOf(1L, 2L)) } returns listOf(product1, product2)

            val result = likeFacade.getMyLikes(
                authedUserId = 7L,
                query = GetMyLikesQuery(userId = 7L, paging = PageQuery(page = 0, size = 20)),
            )

            assertThat(result.content.map { it.name }).containsExactly("A", "B")
            assertThat(result.page).isEqualTo(0)
            assertThat(result.size).isEqualTo(20)
            assertThat(result.totalElements).isEqualTo(5L)
            assertThat(result.totalPages).isEqualTo(3)
        }

        @Test
        @DisplayName("좋아요한 상품이 없으면 빈 content 와 페이지 메타가 반환된다")
        fun returnsEmptyWhenNoLikes() {
            every { likeRepository.findAllByUserId(7L, 0, 20) } returns
                PageResult(content = emptyList(), page = 0, size = 20, totalElements = 0L, totalPages = 0)
            every { productRepository.findAllByIds(emptyList()) } returns emptyList()

            val result = likeFacade.getMyLikes(
                authedUserId = 7L,
                query = GetMyLikesQuery(userId = 7L, paging = PageQuery(page = 0, size = 20)),
            )

            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0L)
        }

        @Test
        @DisplayName("인증된 회원과 다른 식별자로 호출하면 LIKE_FORBIDDEN 예외가 발생한다")
        fun throwsWhenOtherUser() {
            val ex = assertThrows<CoreException> {
                likeFacade.getMyLikes(
                    authedUserId = 7L,
                    query = GetMyLikesQuery(userId = 8L, paging = PageQuery(page = 0, size = 20)),
                )
            }
            assertThat(ex.errorType).isEqualTo(LikeErrorType.LIKE_FORBIDDEN)
            verify(exactly = 0) { likeRepository.findAllByUserId(any(), any(), any()) }
        }

        @Test
        @DisplayName("page / size 가 Repository 에 전달된다")
        fun delegatesPaging() {
            every { likeRepository.findAllByUserId(7L, 3, 50) } returns
                PageResult(content = emptyList(), page = 3, size = 50, totalElements = 0L, totalPages = 0)
            every { productRepository.findAllByIds(emptyList()) } returns emptyList()

            likeFacade.getMyLikes(
                authedUserId = 7L,
                query = GetMyLikesQuery(userId = 7L, paging = PageQuery(page = 3, size = 50)),
            )

            verify { likeRepository.findAllByUserId(7L, 3, 50) }
        }

        @DisplayName("페이지 크기 50 에서도 상품 조회는 findAllByIds 1회로 끝나고 findById N+1 이 없다")
        @Test
        fun batchLoadsProductsForPageSize50() = assertProductLookupIsConstant(size = 50)

        @DisplayName("페이지 크기 100 에서도 상품 조회는 findAllByIds 1회로 끝나고 findById N+1 이 없다")
        @Test
        fun batchLoadsProductsForPageSize100() = assertProductLookupIsConstant(size = 100)

        /**
         * 페이지 크기와 무관하게 상품 조회가 상수(1회 일괄 조회)로 유지되는지 검증한다.
         * 회귀 방지: 좋아요 N 건마다 findById 를 호출하던 N+1 패턴으로 되돌아가면 실패한다.
         */
        private fun assertProductLookupIsConstant(size: Int) {
            val likes = (1L..size.toLong()).map { Like.create(userId = 7L, productId = it) }
            val products = (1L..size.toLong()).map { ProductFixture.validProduct(id = it, name = "P$it") }
            every { likeRepository.findAllByUserId(7L, 0, size) } returns
                PageResult(content = likes, page = 0, size = size, totalElements = size.toLong(), totalPages = 1)
            every { productRepository.findAllByIds(any()) } returns products

            val result = likeFacade.getMyLikes(
                authedUserId = 7L,
                query = GetMyLikesQuery(userId = 7L, paging = PageQuery(page = 0, size = size)),
            )

            assertThat(result.content).hasSize(size)
            verify(exactly = 1) { productRepository.findAllByIds(any()) }
            verify(exactly = 0) { productRepository.findById(any()) }
        }
    }
}
