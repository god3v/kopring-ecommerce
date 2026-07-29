package com.loopers.application.coupon

import com.loopers.application.coupon.command.RegisterCouponCommand
import com.loopers.application.coupon.command.UpdateCouponCommand
import com.loopers.application.coupon.result.AdminCouponResult
import com.loopers.application.coupon.result.CouponIssueResult
import com.loopers.application.coupon.result.FirstComeIssueRequestResult
import com.loopers.application.coupon.result.FirstComeIssueResult
import com.loopers.application.coupon.result.IssuedCouponResult
import com.loopers.application.coupon.result.MyCouponResult
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.coupon.Coupon
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponIssueRequestedEvent
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestRepository
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CouponFacade(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
    private val issueRequestRepository: IssueRequestRepository,
    private val eventPublisher: DomainEventPublisher,
) {
    /**
     * 선착순 발급 요청을 즉시 접수한다 — 존재·선착순 대상·발급 가능 구간만 확인하고 접수 레코드(REQUESTED)를 남긴 뒤
     * `commerce-streamer` 로 처리 이벤트를 발행한다. 한도 소진·중복(1인 1매) 은 여기서가 아니라 처리 결과로 확정된다.
     */
    @Transactional
    fun requestFirstComeIssue(userId: Long, couponId: Long): FirstComeIssueRequestResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.ensureFirstCome()
        coupon.ensureIssuable(LocalDateTime.now())
        val saved = issueRequestRepository.save(IssueRequest.request(userId = userId, couponId = couponId))
        eventPublisher.publish(
            CouponIssueRequestedEvent(couponId = couponId, userId = userId, requestId = saved.requestId),
        )
        return FirstComeIssueRequestResult.of(saved)
    }

    @Transactional(readOnly = true)
    fun getIssueResult(userId: Long, requestId: String): FirstComeIssueResult {
        val request = issueRequestRepository.findByRequestId(requestId)
        if (request == null || request.userId != userId) {
            throw CoreException(CouponErrorType.ISSUE_REQUEST_NOT_FOUND)
        }
        return FirstComeIssueResult.of(request)
    }

    @Transactional
    fun issueCoupon(userId: Long, couponId: Long): IssuedCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.ensureNotFirstCome()
        coupon.ensureIssuable(LocalDateTime.now())
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw CoreException(CouponErrorType.ALREADY_ISSUED_COUPON)
        }
        // 발급 시점에 템플릿의 사용 가능 구간을 발급 쿠폰으로 스냅샷한다 — 이후 템플릿이 바뀌어도 발급분 유효기간은 고정.
        val saved = userCouponRepository.save(
            UserCoupon.issue(
                userId = userId,
                couponId = couponId,
                usableFrom = coupon.useStartAt,
                expiredAt = coupon.useEndAt,
            ),
        )
        return IssuedCouponResult.of(saved, coupon)
    }

    @Transactional(readOnly = true)
    fun getMyCoupons(userId: Long, pageQuery: PageQuery): PageResult<MyCouponResult> {
        val now = LocalDateTime.now()
        val page = userCouponRepository.findAllByUserId(userId, pageQuery.page, pageQuery.size)
        val coupons = couponRepository.findAllByIdsIncludingDeleted(page.content.map { it.couponId })
            .associateBy { it.id }
        return page.map { userCoupon ->
            val coupon = coupons[userCoupon.couponId]
                ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
            MyCouponResult.of(userCoupon, coupon, now)
        }
    }

    @Transactional(readOnly = true)
    fun getCouponsForAdmin(pageQuery: PageQuery): PageResult<AdminCouponResult> =
        couponRepository.findAll(pageQuery.page, pageQuery.size).map { AdminCouponResult.from(it) }

    @Transactional(readOnly = true)
    fun getCouponForAdmin(couponId: Long): AdminCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        return AdminCouponResult.from(coupon)
    }

    @Transactional
    fun registerCoupon(command: RegisterCouponCommand): AdminCouponResult {
        val coupon = Coupon.create(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueStartAt = command.issueStartAt,
            issueEndAt = command.issueEndAt,
            useStartAt = command.useStartAt,
            useEndAt = command.useEndAt,
            now = LocalDateTime.now(),
        )
        return AdminCouponResult.from(couponRepository.save(coupon))
    }

    @Transactional
    fun updateCoupon(couponId: Long, command: UpdateCouponCommand): AdminCouponResult {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.update(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            issueStartAt = command.issueStartAt,
            issueEndAt = command.issueEndAt,
            useStartAt = command.useStartAt,
            useEndAt = command.useEndAt,
            now = LocalDateTime.now(),
        )
        return AdminCouponResult.from(couponRepository.save(coupon))
    }

    @Transactional
    fun deleteCoupon(couponId: Long) {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        coupon.softDelete(LocalDateTime.now())
        couponRepository.save(coupon)
    }

    @Transactional(readOnly = true)
    fun getCouponIssues(couponId: Long, pageQuery: PageQuery): PageResult<CouponIssueResult> {
        couponRepository.findById(couponId)
            ?: throw CoreException(CouponErrorType.COUPON_NOT_FOUND)
        val now = LocalDateTime.now()
        return userCouponRepository.findAllByCouponId(couponId, pageQuery.page, pageQuery.size)
            .map { CouponIssueResult.of(it, now) }
    }
}
