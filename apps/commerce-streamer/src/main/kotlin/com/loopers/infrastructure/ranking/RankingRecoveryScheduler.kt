package com.loopers.infrastructure.ranking

import com.loopers.application.ranking.RankingFacade
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

/**
 * 랭킹판 유실 자가 복구 스케줄러 — 주기적으로 오늘 판의 유실 여부를 점검하고, 유실이면 시간별 집계(RDB SoT)로 재구축한다.
 * 기동 직후에도 한 번 점검하므로 Redis 유실 후 재시작만으로 복구가 시작된다.
 * 유실 판정과 재구축은 Facade 가 소유한다 — 여기는 주기와 오늘 날짜만 정한다.
 * 테스트 프로필에서는 판을 흔들지 않도록 끈다 — 테스트는 `RankingFacade.recoverIfLost` 를 직접 호출한다.
 */
@Component
@Profile("!test")
class RankingRecoveryScheduler(
    private val rankingFacade: RankingFacade,
) {
    @Scheduled(fixedDelayString = "\${loopers.ranking.recovery.check-interval-ms:300000}")
    fun check() {
        rankingFacade.recoverIfLost(LocalDate.now(SEOUL))
    }

    companion object {
        private val SEOUL = ZoneId.of("Asia/Seoul")
    }
}
