package com.loopers.interfaces.api.config

import com.loopers.interfaces.api.auth.AdminAuthInterceptor
import com.loopers.interfaces.api.auth.AuthInterceptor
import com.loopers.interfaces.api.auth.LoginUserArgumentResolver
import com.loopers.interfaces.api.queue.EntryTokenInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfig(
    private val authInterceptor: AuthInterceptor,
    private val entryTokenInterceptor: EntryTokenInterceptor,
    private val adminAuthInterceptor: AdminAuthInterceptor,
    private val loginUserArgumentResolver: LoginUserArgumentResolver,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(authInterceptor)
        // authInterceptor 이후에 등록 — 관문은 인증된 유저(AUTHENTICATED_USER)의 토큰을 확인한다.
        registry.addInterceptor(entryTokenInterceptor).addPathPatterns("/api/v1/orders")
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api-admin/**")
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(loginUserArgumentResolver)
    }
}
