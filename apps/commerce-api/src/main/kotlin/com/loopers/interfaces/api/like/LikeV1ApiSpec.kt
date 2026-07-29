package com.loopers.interfaces.api.like

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Like V1 API", description = "좋아요 API")
interface LikeV1ApiSpec {
    @Operation(
        summary = "좋아요 등록",
        description = "인증된 회원이 상품에 좋아요를 등록합니다. 이미 등록된 경우에도 동일한 성공 응답을 반환합니다(멱등).",
    )
    fun like(user: AuthUser, productId: Long): ApiResponse<Any>

    @Operation(
        summary = "좋아요 취소",
        description = "인증된 회원이 상품의 좋아요를 취소합니다. 좋아요가 없는 경우에도 동일한 성공 응답을 반환합니다(멱등).",
    )
    fun unlike(user: AuthUser, productId: Long): ApiResponse<Any>

    @Operation(
        summary = "내 좋아요 목록 조회",
        description = "인증된 회원이 자신이 좋아요한 상품 목록을 좋아요한 시각 내림차순으로 페이지 단위 조회합니다.",
    )
    fun getMyLikes(user: AuthUser, userId: Long, pageable: Pageable): ApiResponse<LikeV1Dto.LikedProductsResponse>
}
