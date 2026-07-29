package com.loopers.support.page

/** 프레임워크 독립 페이지 조회 입력. [PageResult] 의 입력 짝. */
data class PageQuery(
    val page: Int,
    val size: Int,
)
