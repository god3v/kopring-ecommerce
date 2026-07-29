package com.loopers.domain.ranking

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 일간 랭킹판 키 계산 — `rank:all:{yyyyMMdd}`.
 * 랭킹판을 쓰는 쪽과 읽는 쪽이 각자 이 키를 만들어 같은 랭킹판을 가리킨다.
 * 이 포맷은 두 앱이 반드시 동일하게 유지해야 하는 발행 계약이다 — 바꾸면 양쪽을 함께 바꾼다. 포맷은 테스트로 고정한다.
 */
object RankingKey {
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    fun of(date: LocalDate): String = "rank:all:${date.format(DATE_FORMAT)}"

    // 행동 발생 시각은 Asia/Seoul 벽시계 기준으로 그날 랭킹판에 귀속된다. 두 앱 모두 Asia/Seoul 로 고정돼 날짜부만 취한다.
    fun of(occurredAt: LocalDateTime): String = of(occurredAt.toLocalDate())
}
