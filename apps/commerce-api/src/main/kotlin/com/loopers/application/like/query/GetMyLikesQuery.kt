package com.loopers.application.like.query

import com.loopers.support.page.PageQuery

data class GetMyLikesQuery(
    val userId: Long,
    val paging: PageQuery,
)
