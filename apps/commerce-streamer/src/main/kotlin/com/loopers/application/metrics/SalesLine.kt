package com.loopers.application.metrics

/**
 * 주문 라인의 집계 관점 표현 — 어떤 상품이 몇 개 팔렸는가. 리스너가 이벤트 봉투를 이 값으로 번역한다.
 */
data class SalesLine(val productId: Long, val quantity: Int)
