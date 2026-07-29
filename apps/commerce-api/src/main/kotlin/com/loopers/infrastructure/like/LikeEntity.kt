package com.loopers.infrastructure.like

import com.loopers.domain.BaseEntity
import com.loopers.domain.like.Like
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.ZoneId

@Entity
@Table(
    name = "likes",
    uniqueConstraints = [UniqueConstraint(name = "uk_likes_user_product", columnNames = ["user_id", "product_id"])],
)
class LikeEntity private constructor(
    userId: Long,
    productId: Long,
) : BaseEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: Long = userId
        protected set

    @Column(name = "product_id", nullable = false)
    var productId: Long = productId
        protected set

    fun toDomain(): Like = Like(
        id = this.id,
        userId = this.userId,
        productId = this.productId,
        likedAt = this.createdAt.withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
    )

    companion object {
        fun from(like: Like): LikeEntity = LikeEntity(userId = like.userId, productId = like.productId)
    }
}
