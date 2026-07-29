package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingFacade
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 랭킹 이월 스케줄러 — 매일 자정 직전(23:50)에 오늘 판 점수 일부를 내일 판의 출발점으로 복사한다.
 * 자정 직전이라 하루 점수 대부분이 반영되고, 23:50 이후 유입분은 이월에서 빠진다(허용된 한계).
 * 내일 판이 이미 있으면 저장소가 건너뛰므로 중복 실행·오발동이 실점수를 덮어쓰지 않는다.
 * 테스트 프로필에서는 랭킹판을 흔들지 않도록 끈다 — 테스트는 `RankingFacade.carryOverToTomorrow` 를 직접 호출한다.
 */
@Component
@Profile("!test")
class RankingCarryOverScheduler(
    private val rankingFacade: RankingFacade,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${loopers.ranking.carry-over.cron:$DEFAULT_CRON}", zone = "Asia/Seoul")
    fun carryOver() {
        val today = LocalDate.now(SEOUL)
        rankingFacade.carryOverToTomorrow(today)
        log.info("랭킹 이월 실행 — {} 판 점수를 {} 판의 출발점으로 복사", today, today.plusDays(1))
    }

    companion object {
        const val DEFAULT_CRON = "0 50 23 * * *"
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
