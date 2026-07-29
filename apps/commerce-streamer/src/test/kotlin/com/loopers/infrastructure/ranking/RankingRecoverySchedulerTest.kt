package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingFacade
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class RankingRecoverySchedulerTest {
    @Test
    fun `실행하면 오늘(Asia_Seoul) 날짜 기준으로 복구 점검을 위임한다`() {
        val rankingFacade = mockk<RankingFacade>(relaxed = true)

        RankingRecoveryScheduler(rankingFacade).check()

        verify { rankingFacade.recoverIfLost(LocalDate.now(ZoneId.of("Asia/Seoul"))) }
    }
}
