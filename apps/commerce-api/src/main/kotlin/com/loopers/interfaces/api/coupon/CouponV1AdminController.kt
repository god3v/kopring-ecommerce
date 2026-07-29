package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.CouponFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.support.page.PageQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api-admin/v1/coupons")
class CouponV1AdminController(
    private val couponFacade: CouponFacade,
) : CouponV1AdminApiSpec {
    @GetMapping
    override fun getCoupons(
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<CouponV1Dto.AdminCouponsResponse> {
        val result = couponFacade.getCouponsForAdmin(PageQuery(page = pageable.pageNumber, size = pageable.pageSize))
        return ApiResponse.success(CouponV1Dto.AdminCouponsResponse.from(result))
    }

    @GetMapping("/{couponId}")
    override fun getCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<CouponV1Dto.AdminCouponResponse> {
        val result = couponFacade.getCouponForAdmin(couponId)
        return ApiResponse.success(CouponV1Dto.AdminCouponResponse.from(result))
    }

    @PostMapping
    override fun registerCoupon(
        @RequestBody request: CouponV1Dto.RegisterCouponRequest,
    ): ApiResponse<CouponV1Dto.AdminCouponResponse> {
        val result = couponFacade.registerCoupon(request.toCommand())
        return ApiResponse.success(CouponV1Dto.AdminCouponResponse.from(result))
    }

    @PutMapping("/{couponId}")
    override fun updateCoupon(
        @PathVariable couponId: Long,
        @RequestBody request: CouponV1Dto.UpdateCouponRequest,
    ): ApiResponse<CouponV1Dto.AdminCouponResponse> {
        val result = couponFacade.updateCoupon(couponId, request.toCommand())
        return ApiResponse.success(CouponV1Dto.AdminCouponResponse.from(result))
    }

    @DeleteMapping("/{couponId}")
    override fun deleteCoupon(
        @PathVariable couponId: Long,
    ): ApiResponse<Any> {
        couponFacade.deleteCoupon(couponId)
        return ApiResponse.success()
    }

    @GetMapping("/{couponId}/issues")
    override fun getCouponIssues(
        @PathVariable couponId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<CouponV1Dto.CouponIssuesResponse> {
        val result = couponFacade.getCouponIssues(
            couponId,
            PageQuery(page = pageable.pageNumber, size = pageable.pageSize),
        )
        return ApiResponse.success(CouponV1Dto.CouponIssuesResponse.from(result))
    }
}
