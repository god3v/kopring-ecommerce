package com.loopers.interfaces.api.auth

import com.loopers.application.user.UserFacade
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

@Component
class AuthInterceptor(
    private val userFacade: UserFacade,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        val requiresAuth = handler.hasMethodAnnotation(RequireAuth::class.java)

        val loginId = request.getHeader(HEADER_LOGIN_ID)
        val loginPw = request.getHeader(HEADER_LOGIN_PW)
        // 헤더가 없으면: @RequireAuth 면 거부(401), 아니면 비인증으로 통과한다.
        if (loginId.isNullOrBlank() || loginPw.isNullOrBlank()) {
            if (requiresAuth) {
                throw CoreException(UserErrorType.UNAUTHORIZED)
            }
            return true
        }
        // 헤더가 있으면 @RequireAuth 유무와 무관하게 인증을 시도한다.
        try {
            val user = userFacade.authenticate(loginId, loginPw)
            request.setAttribute(ATTRIBUTE_AUTH_USER, AuthUser(id = user.id, loginId = user.loginId))
        } catch (e: CoreException) {
            // 인증 실패(UNAUTHORIZED): @RequireAuth 면 거부(401), 아니면 선택 인증이므로 삼키고 통과한다.
            // 그 외 CoreException 은 인증 실패가 아니므로 선택 인증이라도 숨기지 않고 그대로 전파한다.
            if (requiresAuth || e.errorType != UserErrorType.UNAUTHORIZED) {
                throw e
            }
        }
        return true
    }

    companion object {
        const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"
        const val ATTRIBUTE_AUTH_USER = "AUTHENTICATED_USER"
    }
}
