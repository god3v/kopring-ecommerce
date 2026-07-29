package com.loopers.interfaces.api.coupon

import com.loopers.interfaces.api.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Pageable

@Tag(name = "Coupon Admin V1 API", description = "관리자 쿠폰 API")
interface CouponV1AdminApiSpec {
    @Operation(
        summary = "(관리자) 쿠폰 템플릿 목록 조회",
        description = "삭제 마크되지 않은 쿠폰 템플릿을 최신순으로 페이지 단위 조회합니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getCoupons(pageable: Pageable): ApiResponse<CouponV1Dto.AdminCouponsResponse>

    @Operation(
        summary = "(관리자) 쿠폰 템플릿 상세 조회",
        description = "템플릿 식별자로 단일 템플릿을 조회합니다. 미존재/삭제 마크는 404 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getCoupon(couponId: Long): ApiResponse<CouponV1Dto.AdminCouponResponse>

    @Operation(
        summary = "(관리자) 쿠폰 템플릿 등록",
        description = "정액(FIXED)/정률(RATE) 템플릿을 등록합니다. 입력 형식 위반은 400 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun registerCoupon(request: CouponV1Dto.RegisterCouponRequest): ApiResponse<CouponV1Dto.AdminCouponResponse>

    @Operation(
        summary = "(관리자) 쿠폰 템플릿 수정",
        description = "템플릿 정보를 수정합니다. 미존재/삭제 마크는 404, 입력 형식 위반은 400 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun updateCoupon(couponId: Long, request: CouponV1Dto.UpdateCouponRequest): ApiResponse<CouponV1Dto.AdminCouponResponse>

    @Operation(
        summary = "(관리자) 쿠폰 템플릿 삭제",
        description = "템플릿을 삭제 마크합니다. 이미 발급된 쿠폰은 유지됩니다. 미존재/이미 삭제는 404 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun deleteCoupon(couponId: Long): ApiResponse<Any>

    @Operation(
        summary = "(관리자) 특정 쿠폰 발급 내역 조회",
        description = "해당 템플릿으로 발급된 발급 쿠폰을 발급 최신순으로 페이지 단위 조회합니다. 미존재/삭제 마크 템플릿은 404 입니다. 관리자 인증 필요(X-Loopers-Ldap).",
    )
    fun getCouponIssues(couponId: Long, pageable: Pageable): ApiResponse<CouponV1Dto.CouponIssuesResponse>
}
