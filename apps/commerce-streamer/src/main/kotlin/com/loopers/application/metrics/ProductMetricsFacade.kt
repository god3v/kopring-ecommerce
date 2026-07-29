package com.loopers.application.metrics

import com.loopers.domain.metrics.ProcessedEventRepository
import com.loopers.domain.metrics.ProductHourlyMetrics
import com.loopers.domain.metrics.ProductMetrics
import com.loopers.domain.metrics.ProductHourlyMetricsRepository
import com.loopers.domain.metrics.ProductMetricsRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

/**
 * 상품 지표 집계의 트랜잭션 경계·조율을 단일 소유한다.
 * 각 연산은 멱등 표식(event_handled) 기록과 지표 upsert 를 **같은 트랜잭션**에서 처리한다 —
 * 처리 후 ack 전에 크래시가 나 같은 메시지가 재전달돼도, 멱등 표식이 남아 있어 결과는 1회만 반영된다.
 * 누적 지표와 함께 시간별 버킷(랭킹 재계산의 원본)도 같은 트랜잭션에서 발생 시각으로 귀속해 누적한다.
 *
 * 이벤트 타입은 리스너가 도메인 의도로 번역해 넘긴다 — 이 컴포넌트는 eventType 문자열을 모른다.
 */
@Component
class ProductMetricsFacade(
    private val productMetricsRepository: ProductMetricsRepository,
    private val productHourlyMetricsRepository: ProductHourlyMetricsRepository,
    private val processedEventRepository: ProcessedEventRepository,
) {
    @Transactional
    fun increaseLike(eventId: UUID, productId: Long, occurredAt: LocalDateTime) = onlyOnce(eventId) {
        val metrics = apply(productId) { it.increaseLike() }
        if (!metrics.deleted) accumulateHourly(productId, occurredAt) { it.increaseLike() }
    }

    @Transactional
    fun decreaseLike(eventId: UUID, productId: Long, occurredAt: LocalDateTime) = onlyOnce(eventId) {
        val metrics = apply(productId) { it.decreaseLike() }
        if (!metrics.deleted) accumulateHourly(productId, occurredAt) { it.decreaseLike() }
    }

    @Transactional
    fun increaseView(eventId: UUID, productId: Long, occurredAt: LocalDateTime) = onlyOnce(eventId) {
        val metrics = apply(productId) { it.increaseView() }
        if (!metrics.deleted) accumulateHourly(productId, occurredAt) { it.increaseView() }
    }

    @Transactional
    fun addSales(eventId: UUID, lines: List<SalesLine>, occurredAt: LocalDateTime) = onlyOnce(eventId) {
        // 여러 행을 잠글 때는 productId 오름차순으로 고정해 락 획득 순서를 통일한다(교차 대기 데드락 방지).
        lines.sortedBy { it.productId }.forEach { line ->
            val metrics = apply(line.productId) { it.addSales(line.quantity) }
            if (!metrics.deleted) accumulateHourly(line.productId, occurredAt) { it.addOrderQuantity(line.quantity) }
        }
    }

    /**
     * 삭제 상품의 시간별 집계를 걷어내고 지표 행에 삭제 표식을 남긴다 — 삭제 뒤 도착하는 지연 신호(다른 토픽의
     * 주문 이벤트 등)가 시간별 집계를 되살려 재구축 시 랭킹에 재노출되는 것을 막는다.
     * 표식과 정리는 누적 경로와 같은 지표 행 잠금 아래에서 실행돼 동시 누적과 직렬화된다.
     * 누적 지표의 수치는 계속 쌓인다 — 실제 발생한 사실의 분석 이력이고, 상품 노출은 소프트 삭제가 이미 막는다.
     */
    @Transactional
    fun removeProduct(eventId: UUID, productId: Long) = onlyOnce(eventId) {
        apply(productId) { it.markDeleted() }
        productHourlyMetricsRepository.removeByProductId(productId)
    }

    private fun onlyOnce(eventId: UUID, block: () -> Unit) {
        if (processedEventRepository.existsByEventId(eventId)) return
        block()
        processedEventRepository.save(eventId)
    }

    /**
     * 지표 행을 비관 락으로 잠근 뒤 증분을 반영하고, 잠긴 상태의 지표를 돌려준다 — catalog(key=productId)·
     * order(key=orderId) 두 소비자가 같은 상품 지표를 동시에 갱신할 수 있어, 락 없는 read-modify-write 는
     * 앞선 증분을 덮어쓴다. 이 잠금이 삭제 표식과 시간별 누적의 직렬화 지점이기도 하다.
     * 행이 아직 없으면 새로 만든다. 동시 최초 생성 경합은 product_id UNIQUE 가 한쪽을 실패시키고,
     * 실패한 쪽은 멱등 표식 없이 롤백되므로 재전달에서 락 경로로 수렴한다.
     */
    private fun apply(productId: Long, change: (ProductMetrics) -> Unit): ProductMetrics {
        val metrics = productMetricsRepository.findByProductIdForUpdate(productId) ?: ProductMetrics.create(productId)
        change(metrics)
        return productMetricsRepository.save(metrics)
    }

    // 시간별 버킷은 원자 upsert 로 누적한다 — 비관 락·생성 경합 처리가 필요 없다.
    private fun accumulateHourly(productId: Long, occurredAt: LocalDateTime, change: (ProductHourlyMetrics) -> Unit) {
        val delta = ProductHourlyMetrics.create(productId, occurredAt)
        change(delta)
        productHourlyMetricsRepository.accumulate(delta)
    }
}
