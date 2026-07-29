package com.loopers.interfaces.api.ranking

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Ranking V1 API", description = "상품 랭킹 조회 API")
interface RankingV1ApiSpec {
    @Operation(
        summary = "랭킹 페이지 조회",
        description = "지정한 기간(DAILY|WEEKLY|MONTHLY, 대소문자 무관, 미지정 시 DAILY)과 날짜(미지정 시 오늘, Asia/Seoul)의 " +
            "랭킹을 점수 내림차순으로 페이지 단위 조회합니다. 일간은 실시간 랭킹판, 주간·월간은 배치가 적재한 " +
            "TOP 100 기준이며 date 가 속한 ISO 주·달력 월로 해석됩니다. " +
            "상품 ID 만이 아니라 이름·가격·브랜드·좋아요 수가 조립돼 반환됩니다. 인증이 필요하지 않습니다. " +
            "period 가 목록 밖이거나 date 형식이 yyyyMMdd 가 아니면 400 입니다.",
    )
    fun getRankings(
        period: String?,
        date: String?,
        page: Int,
        size: Int,
    ): ApiResponse<RankingV1Dto.RankingsResponse>
}
