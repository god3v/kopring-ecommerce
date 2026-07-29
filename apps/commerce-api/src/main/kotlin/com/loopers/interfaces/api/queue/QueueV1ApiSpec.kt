package com.loopers.interfaces.api.queue

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Queue V1 API", description = "주문 대기열 API")
interface QueueV1ApiSpec {
    @Operation(
        summary = "대기열 진입",
        description = "인증 회원을 주문 대기열에 진입시키고 현재 순번을 반환합니다. 이미 진입한 경우 기존 순번을 유지합니다(멱등).",
    )
    fun enter(user: AuthUser): ApiResponse<QueueV1Dto.PositionResponse>

    @Operation(
        summary = "순번 조회 (Polling)",
        description = "현재 순번·전체 대기 인원·예상 대기 시간(초)을 조회합니다. " +
            "입장 처리되면 응답에 entryToken 이 채워지고 순번은 null 이 됩니다 — 그 토큰으로 주문 API 를 호출합니다. " +
            "클라이언트는 응답의 pollIntervalSeconds 만큼 기다린 뒤 다음 조회를 보냅니다(setTimeout, 0~20% jitter 권장). " +
            "주기는 순번 구간별로 서버가 정합니다 — 0~99: 1초 / 100~999: 3초 / 1000+: 5초. " +
            "pollIntervalSeconds = 0 은 폴링 종료 신호입니다: entryToken 이 있으면 주문으로 진행, 순번·토큰 모두 null 이면 만료/이탈이므로 재진입합니다. " +
            "구간별 주기로 폴링 부하가 줄어듭니다 — 대기 1만 명 균등 분포 기준 고정 2초(5,000 QPS) 대비 약 2,200 QPS.",
    )
    fun position(user: AuthUser): ApiResponse<QueueV1Dto.PositionResponse>
}
