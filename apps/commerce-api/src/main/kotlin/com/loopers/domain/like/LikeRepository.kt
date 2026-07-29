package com.loopers.domain.like

import com.loopers.support.page.PageResult

interface LikeRepository {
    fun save(like: Like): Like
    fun findByUserIdAndProductId(userId: Long, productId: Long): Like?
    fun existsByUserIdAndProductId(userId: Long, productId: Long): Boolean
    fun delete(like: Like): Long
    fun findAllByUserId(userId: Long, page: Int, size: Int): PageResult<Like>
}
