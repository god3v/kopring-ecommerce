package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.domain.queue.QueueErrorType
import com.loopers.interfaces.api.auth.AuthInterceptor
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 주문 API 입장 관문 — @RequireEntryToken 이 붙은 핸들러에서 X-Entry-Token 을 검증한다.
 * AuthInterceptor 이후에 실행되어, 이미 인증된 유저(AUTHENTICATED_USER)의 토큰만 확인한다.
 * gate-enabled 를 끄면 검증 없이 통과한다 — 운영 탈출구(대기열 우회)이자 부하 측정용 스위치.
 */
@Component
class EntryTokenInterceptor(
    private val queueFacade: QueueFacade,
    @Value("\${loopers.queue.gate-enabled:true}")
    private val gateEnabled: Boolean,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (!gateEnabled) return true
        if (handler !is HandlerMethod) return true
        if (!handler.hasMethodAnnotation(RequireEntryToken::class.java)) return true

        val user = request.getAttribute(AuthInterceptor.ATTRIBUTE_AUTH_USER) as? AuthUser
            ?: throw CoreException(QueueErrorType.ENTRY_TOKEN_REQUIRED)
        val token = request.getHeader(HEADER_ENTRY_TOKEN)
        if (token.isNullOrBlank()) {
            throw CoreException(QueueErrorType.ENTRY_TOKEN_REQUIRED)
        }
        queueFacade.ensureAdmitted(user.id, token)
        return true
    }

    companion object {
        const val HEADER_ENTRY_TOKEN = "X-Entry-Token"
    }
}
