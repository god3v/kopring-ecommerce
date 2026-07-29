package com.loopers.interfaces.event.like

import com.loopers.application.like.LikeCountUpdater
import com.loopers.domain.like.LikeEvent
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 좋아요 수 집계 리스너 — 좋아요 커밋 이후(AFTER_COMMIT) 비동기로 likeCount 를 반영한다.
 * 집계 실패는 이미 커밋된 좋아요와 무관하며(별도 스레드), 사용자 응답에도 전파되지 않는다.
 */
@Component
class LikeEventListener(
    private val likeCountUpdater: LikeCountUpdater,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handle(event: LikeEvent) {
        when (event) {
            is LikeEvent.Created -> likeCountUpdater.increase(event.productId)
            is LikeEvent.Canceled -> likeCountUpdater.decrease(event.productId)
        }
    }
}
