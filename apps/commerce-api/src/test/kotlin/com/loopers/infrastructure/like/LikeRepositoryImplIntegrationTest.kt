package com.loopers.infrastructure.like

import com.loopers.domain.like.LikeRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.like.Like
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, LikeRepositoryImpl::class)
class LikeRepositoryImplIntegrationTest @Autowired constructor(
    private val likeRepository: LikeRepository,
    private val testEntityManager: TestEntityManager,
) {
    @DisplayName("save / findByUserIdAndProductId 라운드트립")
    @Nested
    inner class SaveAndFind {
        @DisplayName("save 후 findByUserIdAndProductId 로 동일 도메인 객체가 복원된다.")
        @Test
        fun saveThenFind() {
            likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()
            testEntityManager.clear()

            val found = requireNotNull(likeRepository.findByUserIdAndProductId(1L, 10L)) {
                "Like should exist after save"
            }

            assertThat(found.userId).isEqualTo(1L)
            assertThat(found.productId).isEqualTo(10L)
        }

        @DisplayName("(userId, productId) 동일한 두 번째 save 는 unique 제약으로 실패한다.")
        @Test
        fun duplicateSaveFails() {
            likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()

            assertThatThrownBy {
                likeRepository.save(Like.create(userId = 1L, productId = 10L))
                testEntityManager.flush()
            }.isInstanceOf(Throwable::class.java)
        }
    }

    @DisplayName("existsByUserIdAndProductId")
    @Nested
    inner class Exists {
        @DisplayName("존재 여부를 정확히 판정한다.")
        @Test
        fun existsAccurate() {
            likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()

            assertThat(likeRepository.existsByUserIdAndProductId(1L, 10L)).isTrue()
            assertThat(likeRepository.existsByUserIdAndProductId(1L, 99L)).isFalse()
            assertThat(likeRepository.existsByUserIdAndProductId(2L, 10L)).isFalse()
        }
    }

    @DisplayName("delete")
    @Nested
    inner class Delete {
        @DisplayName("delete 가 행을 제거한다.")
        @Test
        fun deleteRemovesRow() {
            val saved = likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()

            likeRepository.delete(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            assertThat(likeRepository.findByUserIdAndProductId(1L, 10L)).isNull()
        }

        @DisplayName("이미 삭제됐거나 존재하지 않는 좋아요를 delete 해도 예외 없이 no-op 으로 끝난다.")
        @Test
        fun deleteIsNoOp_whenRowAbsent() {
            val saved = likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()
            likeRepository.delete(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            // 동일 취소를 한 번 더 (동시/순차 중복) — 행이 이미 없어도 예외가 나지 않아야 한다.
            assertThatNoException().isThrownBy {
                likeRepository.delete(saved)
                testEntityManager.flush()
            }
            assertThat(likeRepository.findByUserIdAndProductId(1L, 10L)).isNull()
        }
    }

    @DisplayName("findAllByUserId")
    @Nested
    inner class FindAllByUserId {
        @DisplayName("likedAt desc 로 정렬된다.")
        @Test
        fun ordersByLikedAtDesc() {
            likeRepository.save(Like.create(userId = 1L, productId = 10L))
            testEntityManager.flush()
            Thread.sleep(10)
            likeRepository.save(Like.create(userId = 1L, productId = 20L))
            testEntityManager.flush()
            Thread.sleep(10)
            likeRepository.save(Like.create(userId = 1L, productId = 30L))
            testEntityManager.flush()
            testEntityManager.clear()

            val result = likeRepository.findAllByUserId(1L, 0, 10)

            assertThat(result.content.map { it.productId }).containsExactly(30L, 20L, 10L)
        }

        @DisplayName("page / size 페이징이 적용되고 totalElements / totalPages 가 정확하다.")
        @Test
        fun appliesPagination() {
            (10L..14L).forEach {
                likeRepository.save(Like.create(userId = 1L, productId = it))
                testEntityManager.flush()
                Thread.sleep(5)
            }
            testEntityManager.clear()

            val page0 = likeRepository.findAllByUserId(1L, 0, 2)
            val page1 = likeRepository.findAllByUserId(1L, 1, 2)

            assertThat(page0.content).hasSize(2)
            assertThat(page1.content).hasSize(2)
            assertThat(page0.content[0].productId).isNotEqualTo(page1.content[0].productId)
            // 전체 5건 / 페이지 크기 2 → 총 3페이지.
            assertThat(page0.totalElements).isEqualTo(5L)
            assertThat(page0.totalPages).isEqualTo(3)
            assertThat(page0.page).isEqualTo(0)
            assertThat(page0.size).isEqualTo(2)
        }

        @DisplayName("다른 user 의 좋아요는 결과에 포함되지 않는다.")
        @Test
        fun otherUsersLikesExcluded() {
            likeRepository.save(Like.create(userId = 1L, productId = 10L))
            likeRepository.save(Like.create(userId = 2L, productId = 20L))
            testEntityManager.flush()
            testEntityManager.clear()

            val result = likeRepository.findAllByUserId(1L, 0, 10)

            assertThat(result.content).hasSize(1)
            assertThat(result.totalElements).isEqualTo(1L)
            assertThat(result.content[0].userId).isEqualTo(1L)
            assertThat(result.content[0].productId).isEqualTo(10L)
        }
    }
}
