package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Coupon V1 API", description = "회원 쿠폰 API")
interface CouponV1ApiSpec {
    @Operation(
        summary = "쿠폰 발급 요청",
        description = "인증된 회원이 쿠폰 템플릿으로부터 쿠폰을 발급받습니다. 만료된 템플릿은 400, 미존재/삭제는 404, 이미 발급(1인 1매) 은 409 입니다.",
    )
    fun issueCoupon(user: AuthUser, couponId: Long): ApiResponse<CouponV1Dto.IssuedCouponResponse>

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "인증된 회원이 보유한 발급 쿠폰을 발급 최신순으로 페이지 단위 조회합니다. 각 항목에 사용 가능/사용 완료/만료 상태가 함께 옵니다.",
    )
    fun getMyCoupons(user: AuthUser, pageable: Pageable): ApiResponse<CouponV1Dto.MyCouponsResponse>

    @Operation(
        summary = "선착순 쿠폰 발급 요청 접수",
        description = "선착순 템플릿의 발급을 즉시 접수하고 요청 식별자를 돌려줍니다(202). " +
            "한도 소진·중복(1인 1매) 은 접수가 아니라 결과 조회로 확정됩니다. 일반 템플릿/발급 기간 밖은 400, 미존재/삭제는 404.",
    )
    fun requestFirstComeIssue(
        user: AuthUser,
        request: CouponV1Dto.FirstComeIssueRequest,
    ): ApiResponse<CouponV1Dto.FirstComeIssueResponse>

    @Operation(
        summary = "선착순 발급 요청 결과 조회",
        description = "요청 식별자로 발급 요청의 현재 상태(접수됨/발급됨/거절됨) 를 조회합니다. 본인 소유가 아니거나 미존재면 404 입니다.",
    )
    fun getIssueResult(user: AuthUser, requestId: String): ApiResponse<CouponV1Dto.FirstComeIssueResultResponse>
}
