package com.loopers.interfaces.api.coupon

import com.loopers.application.coupon.command.RegisterCouponCommand
import com.loopers.application.coupon.command.UpdateCouponCommand
import com.loopers.application.coupon.result.AdminCouponResult
import com.loopers.application.coupon.result.CouponIssueResult
import com.loopers.application.coupon.result.FirstComeIssueRequestResult
import com.loopers.application.coupon.result.FirstComeIssueResult
import com.loopers.application.coupon.result.IssuedCouponResult
import com.loopers.application.coupon.result.MyCouponResult
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.support.page.PageResult
import java.time.LocalDateTime

class CouponV1Dto {
    data class FirstComeIssueRequest(
        val couponId: Long,
    )

    data class FirstComeIssueResponse(
        val requestId: String,
        val couponId: Long,
        val status: IssueRequestStatus,
        val requestedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: FirstComeIssueRequestResult): FirstComeIssueResponse = FirstComeIssueResponse(
                requestId = result.requestId,
                couponId = result.couponId,
                status = result.status,
                requestedAt = result.requestedAt,
            )
        }
    }

    data class FirstComeIssueResultResponse(
        val requestId: String,
        val couponId: Long,
        val status: IssueRequestStatus,
        val rejectReason: RejectReason?,
        val userCouponId: Long?,
        val processedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(result: FirstComeIssueResult): FirstComeIssueResultResponse = FirstComeIssueResultResponse(
                requestId = result.requestId,
                couponId = result.couponId,
                status = result.status,
                rejectReason = result.rejectReason,
                userCouponId = result.issuedUserCouponId,
                processedAt = result.processedAt,
            )
        }
    }

    data class RegisterCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long?,
        val issueStartAt: LocalDateTime,
        val issueEndAt: LocalDateTime,
        val useStartAt: LocalDateTime,
        val useEndAt: LocalDateTime,
    ) {
        fun toCommand(): RegisterCouponCommand = RegisterCouponCommand(
            name = name,
            discountType = DiscountType.from(type),
            discountValue = value,
            minOrderAmount = minOrderAmount,
            issueStartAt = issueStartAt,
            issueEndAt = issueEndAt,
            useStartAt = useStartAt,
            useEndAt = useEndAt,
        )
    }

    data class UpdateCouponRequest(
        val name: String,
        val type: String,
        val value: Long,
        val minOrderAmount: Long?,
        val issueStartAt: LocalDateTime,
        val issueEndAt: LocalDateTime,
        val useStartAt: LocalDateTime,
        val useEndAt: LocalDateTime,
    ) {
        fun toCommand(): UpdateCouponCommand = UpdateCouponCommand(
            name = name,
            discountType = DiscountType.from(type),
            discountValue = value,
            minOrderAmount = minOrderAmount,
            issueStartAt = issueStartAt,
            issueEndAt = issueEndAt,
            useStartAt = useStartAt,
            useEndAt = useEndAt,
        )
    }

    data class IssuedCouponResponse(
        val userCouponId: Long,
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val usableFrom: LocalDateTime,
        val expiredAt: LocalDateTime,
        val status: UserCouponStatus,
        val issuedAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: IssuedCouponResult): IssuedCouponResponse = IssuedCouponResponse(
                userCouponId = result.userCouponId,
                couponId = result.couponId,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                usableFrom = result.usableFrom,
                expiredAt = result.expiredAt,
                status = result.status,
                issuedAt = result.issuedAt,
            )
        }
    }

    data class MyCouponItem(
        val userCouponId: Long,
        val couponId: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val usableFrom: LocalDateTime,
        val expiredAt: LocalDateTime,
        val status: UserCouponStatus,
        val issuedAt: LocalDateTime,
        val usedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(result: MyCouponResult): MyCouponItem = MyCouponItem(
                userCouponId = result.userCouponId,
                couponId = result.couponId,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                usableFrom = result.usableFrom,
                expiredAt = result.expiredAt,
                status = result.status,
                issuedAt = result.issuedAt,
                usedAt = result.usedAt,
            )
        }
    }

    data class MyCouponsResponse(
        val content: List<MyCouponItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<MyCouponResult>): MyCouponsResponse = MyCouponsResponse(
                content = page.content.map { MyCouponItem.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class AdminCouponResponse(
        val id: Long,
        val name: String,
        val type: DiscountType,
        val value: Long,
        val minOrderAmount: Long?,
        val issueStartAt: LocalDateTime,
        val issueEndAt: LocalDateTime,
        val useStartAt: LocalDateTime,
        val useEndAt: LocalDateTime,
    ) {
        companion object {
            fun from(result: AdminCouponResult): AdminCouponResponse = AdminCouponResponse(
                id = result.id,
                name = result.name,
                type = result.type,
                value = result.value,
                minOrderAmount = result.minOrderAmount,
                issueStartAt = result.issueStartAt,
                issueEndAt = result.issueEndAt,
                useStartAt = result.useStartAt,
                useEndAt = result.useEndAt,
            )
        }
    }

    data class AdminCouponsResponse(
        val content: List<AdminCouponResponse>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<AdminCouponResult>): AdminCouponsResponse = AdminCouponsResponse(
                content = page.content.map { AdminCouponResponse.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }

    data class CouponIssueItem(
        val userCouponId: Long,
        val userId: Long,
        val status: UserCouponStatus,
        val issuedAt: LocalDateTime,
        val usedAt: LocalDateTime?,
    ) {
        companion object {
            fun from(result: CouponIssueResult): CouponIssueItem = CouponIssueItem(
                userCouponId = result.userCouponId,
                userId = result.userId,
                status = result.status,
                issuedAt = result.issuedAt,
                usedAt = result.usedAt,
            )
        }
    }

    data class CouponIssuesResponse(
        val content: List<CouponIssueItem>,
        val page: Int,
        val size: Int,
        val totalElements: Long,
        val totalPages: Int,
    ) {
        companion object {
            fun from(page: PageResult<CouponIssueResult>): CouponIssuesResponse = CouponIssuesResponse(
                content = page.content.map { CouponIssueItem.from(it) },
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
            )
        }
    }
}
