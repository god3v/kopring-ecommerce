package com.loopers.application.queue

import com.loopers.application.queue.port.EntryTokenStore
import com.loopers.application.queue.port.WaitingQueueRepository
import com.loopers.domain.queue.EntryToken
import com.loopers.domain.queue.QueueErrorType
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

@DisplayName("QueueFacade")
class QueueFacadeTest {
    private val waitingQueueRepository: WaitingQueueRepository = mockk(relaxed = true)
    private val entryTokenStore: EntryTokenStore = mockk(relaxed = true)
    private val queueFacade = QueueFacade(waitingQueueRepository, entryTokenStore, TOKEN_TTL_SECONDS, THROUGHPUT_PER_SECOND)

    @Test
    @DisplayName("enter — 대기열에 진입시키고 순번·전체 인원·예상 시간을 반환한다")
    fun enterReturnsPosition() {
        every { waitingQueueRepository.enter(eq(7L), any()) } returns true
        every { waitingQueueRepository.rank(7L) } returns 360L
        every { waitingQueueRepository.size() } returns 500L
        every { entryTokenStore.find(7L) } returns null

        val result = queueFacade.enter(7L)

        verify { waitingQueueRepository.enter(eq(7L), any<Instant>()) }
        assertThat(result.position).isEqualTo(360L)
        assertThat(result.totalWaiting).isEqualTo(500L)
        assertThat(result.estimatedWaitSeconds).isEqualTo(2L) // 360 / 180 = 2
        assertThat(result.entryToken).isNull()
    }

    @Test
    @DisplayName("position — 대기열에 없으면 position·예상시간 0, 전체 인원만 반환한다")
    fun positionIsNullWhenAbsent() {
        every { waitingQueueRepository.rank(404L) } returns null
        every { waitingQueueRepository.size() } returns 5L
        every { entryTokenStore.find(404L) } returns null

        val result = queueFacade.position(404L)

        assertThat(result.position).isNull()
        assertThat(result.totalWaiting).isEqualTo(5L)
        assertThat(result.estimatedWaitSeconds).isEqualTo(0L)
        assertThat(result.entryToken).isNull()
    }

    @Test
    @DisplayName("position — 입장 토큰이 발급됐으면 응답에 토큰을 포함한다")
    fun positionIncludesTokenWhenAdmitted() {
        every { waitingQueueRepository.rank(7L) } returns null // 대기열에서 빠짐
        every { waitingQueueRepository.size() } returns 5L
        every { entryTokenStore.find(7L) } returns EntryToken("token-abc")

        val result = queueFacade.position(7L)

        assertThat(result.position).isNull()
        assertThat(result.entryToken).isEqualTo("token-abc")
    }

    @Test
    @DisplayName("position — 대기 중이면 순번 구간에 맞는 폴링 주기를 포함한다")
    fun positionIncludesPollInterval() {
        every { waitingQueueRepository.rank(7L) } returns 4_213L
        every { waitingQueueRepository.size() } returns 9_800L
        every { entryTokenStore.find(7L) } returns null

        val result = queueFacade.position(7L)

        assertThat(result.pollIntervalSeconds).isEqualTo(5L) // rank 1000+ 구간
    }

    @Test
    @DisplayName("position — 토큰 발급·미진입이면 폴링 주기 0 (폴링 종료 신호)")
    fun pollIntervalIsZeroWhenNotWaiting() {
        every { waitingQueueRepository.rank(any()) } returns null
        every { waitingQueueRepository.size() } returns 5L
        every { entryTokenStore.find(7L) } returns EntryToken("token-abc")
        every { entryTokenStore.find(404L) } returns null

        assertThat(queueFacade.position(7L).pollIntervalSeconds).isEqualTo(0L)
        assertThat(queueFacade.position(404L).pollIntervalSeconds).isEqualTo(0L)
    }

    @Test
    @DisplayName("admit — 꺼낸 인원 각각에 TTL 토큰을 발급하고 발급 수를 반환한다")
    fun admitIssuesTokens() {
        every { waitingQueueRepository.pollNext(3L) } returns listOf(1L, 2L, 3L)

        val issued = queueFacade.admit(3)

        assertThat(issued).isEqualTo(3)
        verify(exactly = 1) { entryTokenStore.issue(eq(1L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
        verify(exactly = 1) { entryTokenStore.issue(eq(2L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
        verify(exactly = 1) { entryTokenStore.issue(eq(3L), any(), Duration.ofSeconds(TOKEN_TTL_SECONDS)) }
    }

    @Test
    @DisplayName("admit — 대기열이 비어 있으면 발급하지 않는다")
    fun admitIssuesNothingWhenEmpty() {
        every { waitingQueueRepository.pollNext(any()) } returns emptyList()

        val issued = queueFacade.admit(18)

        assertThat(issued).isEqualTo(0)
        verify(exactly = 0) { entryTokenStore.issue(any(), any(), any()) }
    }

    @Test
    @DisplayName("ensureAdmitted — 저장된 토큰과 값이 같으면 통과한다")
    fun ensureAdmittedPasses() {
        every { entryTokenStore.find(7L) } returns EntryToken("token-abc")

        queueFacade.ensureAdmitted(7L, "token-abc")
    }

    @Test
    @DisplayName("ensureAdmitted — 토큰이 없으면 ENTRY_TOKEN_INVALID")
    fun ensureAdmittedThrowsWhenMissing() {
        every { entryTokenStore.find(7L) } returns null

        val ex = assertThrows<CoreException> { queueFacade.ensureAdmitted(7L, "token-abc") }
        assertThat(ex.errorType).isEqualTo(QueueErrorType.ENTRY_TOKEN_INVALID)
    }

    @Test
    @DisplayName("ensureAdmitted — 토큰 값이 다르면 ENTRY_TOKEN_INVALID")
    fun ensureAdmittedThrowsWhenMismatch() {
        every { entryTokenStore.find(7L) } returns EntryToken("stored")

        val ex = assertThrows<CoreException> { queueFacade.ensureAdmitted(7L, "given") }
        assertThat(ex.errorType).isEqualTo(QueueErrorType.ENTRY_TOKEN_INVALID)
    }

    @Test
    @DisplayName("leave — 토큰을 회수한다")
    fun leaveRemovesToken() {
        queueFacade.leave(7L)

        verify { entryTokenStore.remove(7L) }
    }

    companion object {
        private const val TOKEN_TTL_SECONDS = 300L
        private const val THROUGHPUT_PER_SECOND = 180.0
    }
}
