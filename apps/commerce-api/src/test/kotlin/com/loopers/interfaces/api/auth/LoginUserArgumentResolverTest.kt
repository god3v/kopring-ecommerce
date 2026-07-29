package com.loopers.interfaces.api.auth

import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest

class LoginUserArgumentResolverTest {
    private val resolver = LoginUserArgumentResolver()

    @DisplayName("supportsParameter 는 @LoginUser 가 붙은 AuthUser 파라미터에 대해 true 를 반환한다.")
    @Test
    fun supportsParameter_returnsTrue_whenLoginUserAnnotatedAuthUser() {
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns true
            every { parameterType } returns AuthUser::class.java
        }

        assertThat(resolver.supportsParameter(parameter)).isTrue()
    }

    @DisplayName("supportsParameter 는 @LoginUser 가 없는 파라미터에 대해 false 를 반환한다.")
    @Test
    fun supportsParameter_returnsFalse_whenAnnotationAbsent() {
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns false
            every { parameterType } returns AuthUser::class.java
        }

        assertThat(resolver.supportsParameter(parameter)).isFalse()
    }

    @DisplayName("supportsParameter 는 @LoginUser 가 AuthUser 가 아닌 타입에 대해 false 를 반환한다.")
    @Test
    fun supportsParameter_returnsFalse_whenParameterTypeIsNotAuthUser() {
        val parameter: MethodParameter = mockk {
            every { hasParameterAnnotation(LoginUser::class.java) } returns true
            every { parameterType } returns String::class.java
        }

        assertThat(resolver.supportsParameter(parameter)).isFalse()
    }

    @DisplayName("resolveArgument 는 request attribute 에 저장된 AuthUser 를 반환한다.")
    @Test
    fun resolveArgument_returnsAuthUserFromAttribute() {
        val authUser = AuthUser(id = 1L, loginId = UserFixture.DEFAULT_LOGIN_ID)
        val request: HttpServletRequest = mockk {
            every { getAttribute(AuthInterceptor.ATTRIBUTE_AUTH_USER) } returns authUser
        }
        val webRequest: NativeWebRequest = mockk {
            every { getNativeRequest(HttpServletRequest::class.java) } returns request
        }

        assertThat(resolver.resolveArgument(mockk(), null, webRequest, null)).isEqualTo(authUser)
    }

    @DisplayName("resolveArgument 는 non-null(필수) 파라미터인데 attribute 가 없으면, UNAUTHORIZED 예외를 던진다.")
    @Test
    fun resolveArgument_throwsUnauthorized_whenAttributeAbsentAndRequired() {
        val request: HttpServletRequest = mockk {
            every { getAttribute(AuthInterceptor.ATTRIBUTE_AUTH_USER) } returns null
        }
        val webRequest: NativeWebRequest = mockk {
            every { getNativeRequest(HttpServletRequest::class.java) } returns request
        }
        val parameter: MethodParameter = mockk {
            every { isOptional } returns false
        }

        val ex = assertThrows<CoreException> {
            resolver.resolveArgument(parameter, null, webRequest, null)
        }
        assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
    }

    @DisplayName("resolveArgument 는 nullable(선택) 파라미터이고 attribute 가 없으면, 거부 없이 null 을 반환한다.")
    @Test
    fun resolveArgument_returnsNull_whenAttributeAbsentAndOptional() {
        val request: HttpServletRequest = mockk {
            every { getAttribute(AuthInterceptor.ATTRIBUTE_AUTH_USER) } returns null
        }
        val webRequest: NativeWebRequest = mockk {
            every { getNativeRequest(HttpServletRequest::class.java) } returns request
        }
        val parameter: MethodParameter = mockk {
            every { isOptional } returns true
        }

        assertThat(resolver.resolveArgument(parameter, null, webRequest, null)).isNull()
    }
}
