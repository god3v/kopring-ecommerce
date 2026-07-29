package com.loopers.application.like

import com.loopers.domain.product.ProductRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class LikeCountUpdater(
    private val productRepository: ProductRepository,
) {
    @Transactional
    fun increase(productId: Long) {
        productRepository.increaseLikeCount(productId)
    }

    @Transactional
    fun decrease(productId: Long) {
        productRepository.decreaseLikeCount(productId)
    }
}
