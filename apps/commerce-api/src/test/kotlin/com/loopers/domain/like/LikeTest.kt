package com.loopers.domain.like

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

@DisplayName("Like 도메인 모델")
class LikeTest {
    @DisplayName("Like.create")
    @Nested
    inner class Create {
        @DisplayName("create 가 likedAt 을 now() 로 설정한다")
        @Test
        fun setsLikedAtToNow() {
            val before = LocalDateTime.now()

            val like = Like.create(userId = 1L, productId = 1L)

            val after = LocalDateTime.now()
            assertThat(like.likedAt).isBetween(before, after)
        }
    }
}
