package com.loopers.domain.like

import java.time.LocalDateTime

class Like internal constructor(
    val id: Long = 0L,
    val userId: Long,
    val productId: Long,
    val likedAt: LocalDateTime,
) {
    companion object {
        fun create(userId: Long, productId: Long): Like = Like(
            userId = userId,
            productId = productId,
            likedAt = LocalDateTime.now(),
        )
    }
}
