package com.loopers.interfaces.api.auth

import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

@Component
class LoginUserArgumentResolver : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(LoginUser::class.java) && parameter.parameterType == AuthUser::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? {
        val request = webRequest.getNativeRequest(HttpServletRequest::class.java)
        val authUser = request?.getAttribute(AuthInterceptor.ATTRIBUTE_AUTH_USER) as? AuthUser
        // 파라미터가 nullable(AuthUser?) 이면 선택 인증 — 미인증 시 null 을 주입한다.
        // non-null(AuthUser) 이면 @RequireAuth 와 짝이며 미인증은 401 로 거부한다.
        if (authUser == null && !parameter.isOptional) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        return authUser
    }
}
