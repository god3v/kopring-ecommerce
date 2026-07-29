package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingFacade
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.scheduling.support.CronExpression
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RankingCarryOverSchedulerTest {
    @Test
    fun `기본 cron 은 매일 23시 50분으로 예약된다`() {
        val cron = CronExpression.parse(RankingCarryOverScheduler.DEFAULT_CRON)

        assertThat(cron.next(LocalDateTime.of(2026, 7, 14, 10, 0)))
            .isEqualTo(LocalDateTime.of(2026, 7, 14, 23, 50))
        // 23:50 실행 직후의 다음 회차는 다음 날 23:50 — 하루 한 번만 돈다.
        assertThat(cron.next(LocalDateTime.of(2026, 7, 14, 23, 50)))
            .isEqualTo(LocalDateTime.of(2026, 7, 15, 23, 50))
    }

    @Test
    fun `실행하면 오늘(Asia_Seoul) 날짜 기준으로 이월을 위임한다`() {
        val rankingFacade = mockk<RankingFacade>(relaxed = true)

        RankingCarryOverScheduler(rankingFacade).carryOver()

        verify { rankingFacade.carryOverToTomorrow(LocalDate.now(ZoneId.of("Asia/Seoul"))) }
    }
}
