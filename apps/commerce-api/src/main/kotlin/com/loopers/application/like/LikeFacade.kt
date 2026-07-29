package com.loopers.application.like

import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.like.LikeRepository
import com.loopers.application.like.query.GetMyLikesQuery
import com.loopers.application.like.result.LikedProductResult
import com.loopers.domain.product.ProductRepository
import com.loopers.domain.like.Like
import com.loopers.domain.like.LikeEvent
import com.loopers.domain.like.LikeErrorType
import com.loopers.domain.product.ProductErrorType
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeFacade(
    private val likeRepository: LikeRepository,
    private val productRepository: ProductRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    @Transactional
    fun like(userId: Long, productId: Long) {
        productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        if (likeRepository.existsByUserIdAndProductId(userId, productId)) {
            return
        }
        likeRepository.save(Like.create(userId = userId, productId = productId))
        eventPublisher.publish(LikeEvent.Created(productId = productId))
    }

    @Transactional
    fun unlike(userId: Long, productId: Long) {
        productRepository.findById(productId)
            ?: throw CoreException(ProductErrorType.PRODUCT_NOT_FOUND)
        val existing = likeRepository.findByUserIdAndProductId(userId, productId) ?: return
        if (likeRepository.delete(existing) > 0) {
            eventPublisher.publish(LikeEvent.Canceled(productId = productId))
        }
    }

    @Transactional(readOnly = true)
    fun getMyLikes(authedUserId: Long, query: GetMyLikesQuery): PageResult<LikedProductResult> {
        if (authedUserId != query.userId) {
            throw CoreException(LikeErrorType.LIKE_FORBIDDEN)
        }
        val likes = likeRepository.findAllByUserId(query.userId, query.paging.page, query.paging.size)
        val products = productRepository.findAllByIds(likes.content.map { it.productId })
            .associateBy { it.id }
        val content = likes.content.mapNotNull { products[it.productId] }
            .map { LikedProductResult.from(it) }
        return PageResult(
            content = content,
            page = likes.page,
            size = likes.size,
            totalElements = likes.totalElements,
            totalPages = likes.totalPages,
        )
    }
}
