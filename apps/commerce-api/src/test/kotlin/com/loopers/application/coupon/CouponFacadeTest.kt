package com.loopers.application.coupon

import com.loopers.application.coupon.command.RegisterCouponCommand
import com.loopers.application.coupon.command.UpdateCouponCommand
import com.loopers.application.support.event.DomainEventPublisher
import com.loopers.domain.coupon.CouponErrorType
import com.loopers.domain.coupon.CouponFixture
import com.loopers.domain.coupon.CouponIssueRequestedEvent
import com.loopers.domain.coupon.CouponRepository
import com.loopers.domain.coupon.DiscountType
import com.loopers.domain.coupon.IssueRequest
import com.loopers.domain.coupon.IssueRequestRepository
import com.loopers.domain.coupon.IssueRequestStatus
import com.loopers.domain.coupon.RejectReason
import com.loopers.domain.coupon.UserCoupon
import com.loopers.domain.coupon.UserCouponRepository
import com.loopers.domain.coupon.UserCouponStatus
import com.loopers.support.error.CoreException
import com.loopers.support.page.PageQuery
import com.loopers.support.page.PageResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

@DisplayName("CouponFacade")
class CouponFacadeTest {
    private val couponRepository: CouponRepository = mockk()
    private val userCouponRepository: UserCouponRepository = mockk()
    private val issueRequestRepository: IssueRequestRepository = mockk()
    private val eventPublisher: DomainEventPublisher = mockk(relaxed = true)
    private val couponFacade = CouponFacade(couponRepository, userCouponRepository, issueRequestRepository, eventPublisher)

    private fun pageOf(content: List<UserCoupon>, total: Long = content.size.toLong()) =
        PageResult(content = content, page = 0, size = 20, totalElements = total, totalPages = 1)

    @Nested
    @DisplayName("issueCoupon — UC-1")
    inner class IssueCoupon {
        @Test
        @DisplayName("유효 템플릿으로 발급하면 AVAILABLE UserCoupon 이 저장되고 결과가 반환된다")
        fun issuesAvailable() {
            val coupon = CouponFixture.coupon(id = 7L)
            every { couponRepository.findById(7L) } returns coupon
            every { userCouponRepository.existsByUserIdAndCouponId(1L, 7L) } returns false
            every { userCouponRepository.save(any()) } answers { firstArg() }

            val result = couponFacade.issueCoupon(userId = 1L, couponId = 7L)

            assertThat(result.couponId).isEqualTo(7L)
            assertThat(result.status).isEqualTo(UserCouponStatus.AVAILABLE)
            verify { userCouponRepository.save(any()) }
        }

        @Test
        @DisplayName("템플릿이 없으면 COUPON_NOT_FOUND")
        fun notFound() {
            every { couponRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { couponFacade.issueCoupon(1L, 99L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
            verify(exactly = 0) { userCouponRepository.save(any()) }
        }

        @Test
        @DisplayName("발급 기간이 지났으면 COUPON_NOT_APPLICABLE")
        fun expired() {
            val coupon = CouponFixture.coupon(
                id = 7L,
                issueStartAt = LocalDateTime.now().minusDays(10),
                issueEndAt = LocalDateTime.now().minusDays(1),
            )
            every { couponRepository.findById(7L) } returns coupon

            val ex = assertThrows<CoreException> { couponFacade.issueCoupon(1L, 7L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
            verify(exactly = 0) { userCouponRepository.save(any()) }
        }

        @Test
        @DisplayName("이미 발급받았으면 ALREADY_ISSUED_COUPON")
        fun alreadyIssued() {
            val coupon = CouponFixture.coupon(id = 7L)
            every { couponRepository.findById(7L) } returns coupon
            every { userCouponRepository.existsByUserIdAndCouponId(1L, 7L) } returns true

            val ex = assertThrows<CoreException> { couponFacade.issueCoupon(1L, 7L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.ALREADY_ISSUED_COUPON)
            verify(exactly = 0) { userCouponRepository.save(any()) }
        }

        @Test
        @DisplayName("선착순 전용(발급 한도 보유) 템플릿을 즉시 발급하면 COUPON_NOT_APPLICABLE")
        fun rejectsFirstComeTemplate() {
            val coupon = CouponFixture.coupon(id = 7L, issueLimit = 100)
            every { couponRepository.findById(7L) } returns coupon

            val ex = assertThrows<CoreException> { couponFacade.issueCoupon(1L, 7L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
            verify(exactly = 0) { userCouponRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("requestFirstComeIssue / getIssueResult — UC-10·11")
    inner class FirstCome {
        @Test
        @DisplayName("유효한 선착순 요청을 접수하면 REQUESTED 레코드가 저장되고 요청 식별자와 함께 이벤트가 발행된다")
        fun accepts() {
            val coupon = CouponFixture.coupon(id = 7L, issueLimit = 100)
            every { couponRepository.findById(7L) } returns coupon
            every { issueRequestRepository.save(any()) } answers { firstArg() }

            val result = couponFacade.requestFirstComeIssue(userId = 1L, couponId = 7L)

            assertThat(result.status).isEqualTo(IssueRequestStatus.REQUESTED)
            assertThat(result.requestId).isNotBlank()
            assertThat(result.couponId).isEqualTo(7L)
            verify { issueRequestRepository.save(any()) }
            verify { eventPublisher.publish(any<CouponIssueRequestedEvent>()) }
        }

        @Test
        @DisplayName("템플릿이 없으면 COUPON_NOT_FOUND (접수 없음)")
        fun notFound() {
            every { couponRepository.findById(99L) } returns null

            val ex = assertThrows<CoreException> { couponFacade.requestFirstComeIssue(1L, 99L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
            verify(exactly = 0) { issueRequestRepository.save(any()) }
        }

        @Test
        @DisplayName("발급 한도가 없는 일반 템플릿이면 COUPON_NOT_APPLICABLE (접수 없음)")
        fun rejectsUnlimited() {
            val coupon = CouponFixture.coupon(id = 7L, issueLimit = null)
            every { couponRepository.findById(7L) } returns coupon

            val ex = assertThrows<CoreException> { couponFacade.requestFirstComeIssue(1L, 7L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
            verify(exactly = 0) { issueRequestRepository.save(any()) }
        }

        @Test
        @DisplayName("발급 가능 구간 밖이면 COUPON_NOT_APPLICABLE (접수 없음)")
        fun rejectsOutOfWindow() {
            val coupon = CouponFixture.coupon(
                id = 7L,
                issueLimit = 100,
                issueStartAt = LocalDateTime.now().minusDays(10),
                issueEndAt = LocalDateTime.now().minusDays(1),
            )
            every { couponRepository.findById(7L) } returns coupon

            val ex = assertThrows<CoreException> { couponFacade.requestFirstComeIssue(1L, 7L) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_NOT_APPLICABLE)
            verify(exactly = 0) { issueRequestRepository.save(any()) }
        }

        @Test
        @DisplayName("getIssueResult: 본인 요청이면 현재 상태를 반환한다")
        fun ownResult() {
            val request = IssueRequest.request(userId = 1L, couponId = 7L)
            every { issueRequestRepository.findByRequestId(request.requestId) } returns request

            val result = couponFacade.getIssueResult(userId = 1L, requestId = request.requestId)

            assertThat(result.requestId).isEqualTo(request.requestId)
            assertThat(result.status).isEqualTo(IssueRequestStatus.REQUESTED)
        }

        @Test
        @DisplayName("getIssueResult: 발급됨 결과는 발급된 쿠폰 식별자를 함께 반환한다")
        fun issuedResult() {
            val request = IssueRequest.request(userId = 1L, couponId = 7L)
                .also { it.confirmIssued(userCouponId = 501L, at = LocalDateTime.now()) }
            every { issueRequestRepository.findByRequestId(request.requestId) } returns request

            val result = couponFacade.getIssueResult(userId = 1L, requestId = request.requestId)

            assertThat(result.status).isEqualTo(IssueRequestStatus.ISSUED)
            assertThat(result.issuedUserCouponId).isEqualTo(501L)
        }

        @Test
        @DisplayName("getIssueResult: 거절됨 결과는 거절 사유를 함께 반환한다")
        fun rejectedResult() {
            val request = IssueRequest.request(userId = 1L, couponId = 7L)
                .also { it.reject(reason = RejectReason.SOLD_OUT, at = LocalDateTime.now()) }
            every { issueRequestRepository.findByRequestId(request.requestId) } returns request

            val result = couponFacade.getIssueResult(userId = 1L, requestId = request.requestId)

            assertThat(result.status).isEqualTo(IssueRequestStatus.REJECTED)
            assertThat(result.rejectReason).isEqualTo(RejectReason.SOLD_OUT)
        }

        @Test
        @DisplayName("getIssueResult: 타인 소유 요청이면 ISSUE_REQUEST_NOT_FOUND")
        fun otherUserRequest() {
            val request = IssueRequest.request(userId = 2L, couponId = 7L)
            every { issueRequestRepository.findByRequestId(request.requestId) } returns request

            val ex = assertThrows<CoreException> { couponFacade.getIssueResult(1L, request.requestId) }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.ISSUE_REQUEST_NOT_FOUND)
        }

        @Test
        @DisplayName("getIssueResult: 미존재 요청이면 ISSUE_REQUEST_NOT_FOUND")
        fun missingRequest() {
            every { issueRequestRepository.findByRequestId("missing") } returns null

            val ex = assertThrows<CoreException> { couponFacade.getIssueResult(1L, "missing") }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.ISSUE_REQUEST_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("getMyCoupons — UC-2")
    inner class GetMyCoupons {
        @Test
        @DisplayName("보유 발급 쿠폰의 노출 상태가 USED/EXPIRED/AVAILABLE 로 파생된다")
        fun derivesStatus() {
            // 노출 상태(EXPIRED) 는 이제 템플릿이 아니라 발급 쿠폰 자신의 expiredAt 으로 파생된다.
            val available = CouponFixture.coupon(id = 1L)
            val used = CouponFixture.coupon(id = 2L)
            val expired = CouponFixture.coupon(id = 3L)
            val ucAvailable = CouponFixture.userCoupon(id = 11L, userId = 1L, couponId = 1L, status = UserCouponStatus.AVAILABLE)
            val ucUsed = CouponFixture.userCoupon(id = 12L, userId = 1L, couponId = 2L, status = UserCouponStatus.USED, usedAt = LocalDateTime.now())
            val ucExpired = CouponFixture.userCoupon(
                id = 13L,
                userId = 1L,
                couponId = 3L,
                status = UserCouponStatus.AVAILABLE,
                expiredAt = LocalDateTime.now().minusDays(1),
            )

            every { userCouponRepository.findAllByUserId(1L, 0, 20) } returns pageOf(listOf(ucAvailable, ucUsed, ucExpired))
            every { couponRepository.findAllByIdsIncludingDeleted(listOf(1L, 2L, 3L)) } returns listOf(available, used, expired)

            val result = couponFacade.getMyCoupons(1L, PageQuery(0, 20))

            assertThat(result.content.map { it.userCouponId to it.status }).containsExactly(
                11L to UserCouponStatus.AVAILABLE,
                12L to UserCouponStatus.USED,
                13L to UserCouponStatus.EXPIRED,
            )
            assertThat(result.totalElements).isEqualTo(3L)
        }

        @Test
        @DisplayName("삭제된 템플릿의 발급 쿠폰도 includingDeleted 조회로 노출된다")
        fun includesDeletedTemplate() {
            val deletedTemplate = CouponFixture.coupon(id = 5L).also { it.softDelete(LocalDateTime.now()) }
            val uc = CouponFixture.userCoupon(id = 50L, userId = 1L, couponId = 5L)
            every { userCouponRepository.findAllByUserId(1L, 0, 20) } returns pageOf(listOf(uc))
            every { couponRepository.findAllByIdsIncludingDeleted(listOf(5L)) } returns listOf(deletedTemplate)

            val result = couponFacade.getMyCoupons(1L, PageQuery(0, 20))

            assertThat(result.content).hasSize(1)
            verify { couponRepository.findAllByIdsIncludingDeleted(listOf(5L)) }
        }
    }

    @Nested
    @DisplayName("관리자 템플릿 — UC-3~7")
    inner class AdminTemplate {
        @Test
        @DisplayName("getCouponForAdmin: 존재하면 결과, 없으면 COUPON_NOT_FOUND")
        fun detail() {
            every { couponRepository.findById(7L) } returns CouponFixture.coupon(id = 7L)
            every { couponRepository.findById(99L) } returns null

            assertThat(couponFacade.getCouponForAdmin(7L).id).isEqualTo(7L)
            assertThat(assertThrows<CoreException> { couponFacade.getCouponForAdmin(99L) }.errorType)
                .isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
        }

        @Test
        @DisplayName("registerCoupon: 유효 입력이면 저장된다")
        fun register() {
            every { couponRepository.save(any()) } answers { firstArg() }

            val result = couponFacade.registerCoupon(
                RegisterCouponCommand(
                    name = "신규가입 10% 할인",
                    discountType = DiscountType.RATE,
                    discountValue = 10,
                    minOrderAmount = 10000,
                    issueStartAt = LocalDateTime.now().minusDays(1),
                    issueEndAt = LocalDateTime.now().plusDays(30),
                    useStartAt = LocalDateTime.now().minusDays(1),
                    useEndAt = LocalDateTime.now().plusDays(60),
                ),
            )

            assertThat(result.type).isEqualTo(DiscountType.RATE)
            verify { couponRepository.save(any()) }
        }

        @Test
        @DisplayName("registerCoupon: 만료 과거면 COUPON_BAD_REQUEST (도메인 검증 위임)")
        fun registerRejectsPast() {
            val ex = assertThrows<CoreException> {
                couponFacade.registerCoupon(
                    RegisterCouponCommand(
                        name = "쿠폰",
                        discountType = DiscountType.FIXED,
                        discountValue = 1000,
                        minOrderAmount = null,
                        issueStartAt = LocalDateTime.now().minusDays(10),
                        issueEndAt = LocalDateTime.now().minusDays(1),
                        useStartAt = LocalDateTime.now().minusDays(10),
                        useEndAt = LocalDateTime.now().plusDays(60),
                    ),
                )
            }
            assertThat(ex.errorType).isEqualTo(CouponErrorType.COUPON_BAD_REQUEST)
            verify(exactly = 0) { couponRepository.save(any()) }
        }

        @Test
        @DisplayName("updateCoupon: 존재하면 갱신, 없으면 COUPON_NOT_FOUND")
        fun update() {
            val coupon = CouponFixture.coupon(id = 7L, name = "기존")
            every { couponRepository.findById(7L) } returns coupon
            every { couponRepository.save(coupon) } returns coupon
            every { couponRepository.findById(99L) } returns null

            val result = couponFacade.updateCoupon(
                7L,
                UpdateCouponCommand(
                    name = "변경",
                    discountType = DiscountType.FIXED,
                    discountValue = 3000,
                    minOrderAmount = 5000,
                    issueStartAt = LocalDateTime.now().minusDays(1),
                    issueEndAt = LocalDateTime.now().plusDays(10),
                    useStartAt = LocalDateTime.now().minusDays(1),
                    useEndAt = LocalDateTime.now().plusDays(20),
                ),
            )

            assertThat(result.name).isEqualTo("변경")
            assertThat(
                assertThrows<CoreException> {
                    couponFacade.updateCoupon(
                        99L,
                        UpdateCouponCommand(
                            name = "x",
                            discountType = DiscountType.FIXED,
                            discountValue = 1,
                            minOrderAmount = null,
                            issueStartAt = LocalDateTime.now().minusDays(1),
                            issueEndAt = LocalDateTime.now().plusDays(1),
                            useStartAt = LocalDateTime.now().minusDays(1),
                            useEndAt = LocalDateTime.now().plusDays(10),
                        ),
                    )
                }.errorType,
            ).isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
        }

        @Test
        @DisplayName("deleteCoupon: 존재하면 soft delete, 없으면 COUPON_NOT_FOUND")
        fun delete() {
            val coupon = CouponFixture.coupon(id = 7L)
            every { couponRepository.findById(7L) } returns coupon
            every { couponRepository.save(coupon) } returns coupon

            couponFacade.deleteCoupon(7L)

            assertThat(coupon.isDeleted()).isTrue()
            verify { couponRepository.save(coupon) }

            every { couponRepository.findById(99L) } returns null
            assertThat(assertThrows<CoreException> { couponFacade.deleteCoupon(99L) }.errorType)
                .isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
        }
    }

    @Nested
    @DisplayName("getCouponIssues — UC-8")
    inner class GetCouponIssues {
        @Test
        @DisplayName("템플릿 존재 시 발급 내역이 매핑된다")
        fun listsIssues() {
            val coupon = CouponFixture.coupon(id = 7L)
            val uc = CouponFixture.userCoupon(id = 70L, userId = 3L, couponId = 7L, status = UserCouponStatus.USED, usedAt = LocalDateTime.now())
            every { couponRepository.findById(7L) } returns coupon
            every { userCouponRepository.findAllByCouponId(7L, 0, 20) } returns pageOf(listOf(uc))

            val result = couponFacade.getCouponIssues(7L, PageQuery(0, 20))

            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].userId).isEqualTo(3L)
            assertThat(result.content[0].status).isEqualTo(UserCouponStatus.USED)
        }

        @Test
        @DisplayName("템플릿이 없으면 COUPON_NOT_FOUND")
        fun notFound() {
            every { couponRepository.findById(99L) } returns null

            assertThat(assertThrows<CoreException> { couponFacade.getCouponIssues(99L, PageQuery(0, 20)) }.errorType)
                .isEqualTo(CouponErrorType.COUPON_NOT_FOUND)
        }
    }
}
