package com.loopers.infrastructure.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.domain.queue.EntryToken
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.time.Duration

/**
 * 실제 Redis(Testcontainer) 로 입장 토큰의 발급·조회·제거·TTL 만료를 검증한다.
 */
@SpringBootTest
@Import(RedisTestContainersConfig::class)
@DisplayName("RedisEntryTokenStore")
class RedisEntryTokenStoreIntegrationTest @Autowired constructor(
    private val entryTokenStore: EntryTokenStore,
    private val redisCleanUp: RedisCleanUp,
) {
    @AfterEach
    fun tearDown() {
        redisCleanUp.truncateAll()
    }

    @Test
    @DisplayName("issue 후 find 로 같은 토큰이 복원된다")
    fun issueThenFind() {
        val token = EntryToken.issue()
        entryTokenStore.issue(1L, token, Duration.ofMinutes(5))

        assertThat(entryTokenStore.find(1L)).isEqualTo(token)
    }

    @Test
    @DisplayName("remove 후 find 는 null 이다")
    fun removeClears() {
        entryTokenStore.issue(1L, EntryToken.issue(), Duration.ofMinutes(5))

        entryTokenStore.remove(1L)

        assertThat(entryTokenStore.find(1L)).isNull()
    }

    @Test
    @DisplayName("발급 안 된 유저의 토큰은 null 이다")
    fun missReturnsNull() {
        assertThat(entryTokenStore.find(999L)).isNull()
    }

    @Test
    @DisplayName("TTL 이 지나면 토큰이 자동 만료된다")
    fun expiresAfterTtl() {
        entryTokenStore.issue(1L, EntryToken.issue(), Duration.ofSeconds(1))

        await().atMost(Duration.ofSeconds(3)).until { entryTokenStore.find(1L) == null }
    }
}
