package com.loopers.interfaces.api.auth

import com.loopers.support.error.CommonErrorType
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 관리자 채널(api-admin) 의 식별·인증 인터셉터.
 *
 * 관리자는 도메인 모델을 두지 않고 헤더로만 식별한다.
 * `X-Loopers-Ldap` 헤더 값이 기대값과 일치해야 통과하며, 누락/불일치는 `401 UNAUTHORIZED`.
 * 적용 경로 한정은 `WebMvcConfig` 의 등록 패턴이 담당한다.
 */
@Component
class AdminAuthInterceptor(
    @Value("\${loopers.admin.ldap-id:loopers.admin}") private val expectedLdap: String,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val ldap = request.getHeader(HEADER_ADMIN_LDAP)
        if (ldap.isNullOrBlank() || ldap != expectedLdap) {
            throw CoreException(CommonErrorType.UNAUTHORIZED)
        }
        return true
    }

    companion object {
        const val HEADER_ADMIN_LDAP = "X-Loopers-Ldap"
    }
}
