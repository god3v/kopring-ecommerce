package com.loopers.domain.ranking

import java.util.UUID

/**
 * 랭킹판 갱신 outbound port. 정렬·점수 누적은 저장소가 원자적으로 처리한다.
 */
interface RankingRepository {
    /**
     * eventId 당 정확히 한 번만 점수를 누적한다. 이미 반영한 이벤트의 재요청은 무시된다.
     * 멱등 판정과 점수 증분이 원자적으로 함께 일어나, 재전달에도 유실·중복이 없다.
     * 랭킹판과 멱등 표식에 보존 기간(ttlSeconds)을 걸어 메모리를 자동 회수한다.
     */
    fun incrementScoreOnce(eventId: UUID, key: String, productId: Long, delta: Double, ttlSeconds: Long)

    /**
     * 주어진 랭킹판 키들에서 상품을 제거한다. 삭제된 상품을 오늘·어제 랭킹판에서 걷어낼 때 쓴다.
     */
    fun removeProduct(keys: List<String>, productId: Long)

    /**
     * 전일 랭킹판(sourceKey)의 점수에 weight 를 곱해 다음 날 랭킹판(destKey)으로 복사한다(콜드 스타트 완화).
     * destKey 가 이미 있으면 아무것도 하지 않는다 — 중복 실행·자정 후 오발동이 실점수를 덮어쓰지 않게 한다.
     * sourceKey 가 없으면 destKey 를 만들지 않는다.
     */
    fun carryOver(sourceKey: String, destKey: String, weight: Double, ttlSeconds: Long)

    /**
     * 랭킹판을 주어진 항목들로 다시 만든다(유실 복구) — 기존 항목은 통째로 대체되고 보존 기간이 설정된다.
     * 임시 키에 쌓아 원자 교체하므로 읽는 쪽이 만들다 만 판을 보지 않는다.
     */
    fun rebuild(key: String, entries: List<RankedEntry>, ttlSeconds: Long)

    /**
     * 랭킹판이 존재하는지 확인한다 — 유실 감지에 쓴다.
     */
    fun exists(key: String): Boolean
}
