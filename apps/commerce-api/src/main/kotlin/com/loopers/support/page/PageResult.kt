package com.loopers.support.page

/** 프레임워크 독립 페이지 조회 결과. Spring `Page` 는 어댑터에서 본 타입으로 변환된다. */
data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
) {
    /** content 를 변환하고 페이지 메타는 그대로 보존한다. */
    fun <R> map(transform: (T) -> R): PageResult<R> = PageResult(
        content = content.map(transform),
        page = page,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
}
