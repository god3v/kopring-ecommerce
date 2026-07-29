package com.loopers.application.queue.result

/**
 * 대기열 순번 조회 결과.
 * @param position 0-based 순번. 대기열에 없으면 null(미진입 또는 입장 완료).
 * @param totalWaiting 전체 대기 인원.
 * @param estimatedWaitSeconds 예상 대기 시간(초). 추정값. 순번이 없으면 0.
 * @param entryToken 입장 토큰. 발급됐으면 값, 아니면 null. null + position null = 미진입/이탈, 값 있음 = 입장 완료.
 * @param pollIntervalSeconds 다음 순번 조회까지 권장 대기 시간(초). 0 = 폴링 종료(토큰으로 주문 진행 또는 재진입).
 */
data class QueuePositionResult(
    val position: Long?,
    val totalWaiting: Long,
    val estimatedWaitSeconds: Long,
    val entryToken: String?,
    val pollIntervalSeconds: Long,
)
