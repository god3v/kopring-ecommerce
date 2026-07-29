package com.loopers.interfaces.api.auth

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.application.user.UserFacade
import com.loopers.domain.user.User
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.interfaces.api.ApiControllerAdvice
import com.loopers.support.error.CommonErrorType
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

class AuthInterceptorTest {
    private val userFacade: UserFacade = mockk()
    private val interceptor = AuthInterceptor(userFacade)
    private val argumentResolver = LoginUserArgumentResolver()
    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(StubController())
        .addInterceptors(interceptor)
        .setCustomArgumentResolvers(argumentResolver)
        .setControllerAdvice(ApiControllerAdvice())
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    @RestController
    class StubController {
        @GetMapping("/test/protected")
        @RequireAuth
        fun protectedEndpoint(@LoginUser user: AuthUser): String = "ok:${user.loginId}:${user.id}"

        @GetMapping("/test/public")
        fun publicEndpoint(): String = "public"

        // 선택 인증: @RequireAuth 없이 nullable AuthUser? 를 주입받는다.
        @GetMapping("/test/optional")
        fun optionalEndpoint(@LoginUser user: AuthUser?): String = "optional:${user?.loginId ?: "anonymous"}"
    }

    @DisplayName("@RequireAuth 가 없는 핸들러에 대해, ")
    @Nested
    inner class WithoutRequireAuth {
        @DisplayName("헤더 없이 호출해도 통과하며 인증을 시도하지 않는다.")
        @Test
        fun passesThrough_whenAnnotationAbsent() {
            // when / then
            mockMvc.perform(get("/test/public"))
                .andExpect(status().isOk)
                .andExpect(content().string("\"public\""))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }
    }

    @DisplayName("선택 인증(@RequireAuth 없이 AuthUser? 주입) 핸들러에 대해, ")
    @Nested
    inner class WithOptionalAuth {
        @DisplayName("헤더 없이 호출하면 거부 없이 통과하고 AuthUser 가 null(anonymous)로 주입된다.")
        @Test
        fun injectsNull_whenNoHeaders() {
            // when / then
            mockMvc.perform(get("/test/optional"))
                .andExpect(status().isOk)
                .andExpect(content().string("\"optional:anonymous\""))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }

        @DisplayName("헤더가 있고 인증에 성공하면 AuthUser 가 주입된다.")
        @Test
        fun injectsAuthUser_whenAuthenticationSucceeds() {
            // give
            every {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            } returns mockk<User> {
                every { id } returns 42L
                every { loginId } returns UserFixture.DEFAULT_LOGIN_ID
            }

            // when / then
            mockMvc.perform(
                get("/test/optional")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"optional:${UserFixture.DEFAULT_LOGIN_ID}\""))

            verify(exactly = 1) {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            }
        }

        @DisplayName("헤더가 있으나 인증에 실패하면 거부 없이 통과하고 AuthUser 가 null(anonymous)로 주입된다.")
        @Test
        fun injectsNull_whenAuthenticationFails() {
            // give
            every {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, "Wrong1234!")
            } throws CoreException(UserErrorType.UNAUTHORIZED)

            // when / then
            mockMvc.perform(
                get("/test/optional")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, "Wrong1234!"),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"optional:anonymous\""))
        }

        @DisplayName("헤더가 공백 문자열이면, 인증을 시도하지 않고 거부 없이 통과한다(anonymous).")
        @Test
        fun injectsNull_whenHeadersBlank() {
            // when / then
            mockMvc.perform(
                get("/test/optional")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, "   ")
                    .header(AuthInterceptor.HEADER_LOGIN_PW, "   "),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"optional:anonymous\""))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }

        @DisplayName("인증이 UNAUTHORIZED 가 아닌 오류를 던지면, 선택 인증이라도 삼키지 않고 그대로 전파한다.")
        @Test
        fun propagatesNonAuthError() {
            // give — authenticate 가 인증 실패가 아닌 일반 오류(INTERNAL_ERROR)를 던지는 상황.
            every {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            } throws CoreException(CommonErrorType.INTERNAL_ERROR)

            // when / then — 200(anonymous)로 숨겨지지 않고 5xx 로 surfaced.
            mockMvc.perform(
                get("/test/optional")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isInternalServerError)
        }
    }

    @DisplayName("@RequireAuth 핸들러에 대해, ")
    @Nested
    inner class WithRequireAuth {
        @DisplayName("loginId 헤더가 누락되면, 401 UNAUTHORIZED 응답이 반환되고 인증은 호출되지 않는다.")
        @Test
        fun returnsUnauthorized_whenLoginIdHeaderMissing() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }

        @DisplayName("loginPw 헤더가 누락되면, 401 UNAUTHORIZED 응답이 반환되고 인증은 호출되지 않는다.")
        @Test
        fun returnsUnauthorized_whenLoginPwHeaderMissing() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }

        @DisplayName("loginId 헤더가 공백 문자열이면, 401 UNAUTHORIZED 응답이 반환된다.")
        @Test
        fun returnsUnauthorized_whenLoginIdHeaderBlank() {
            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, "   ")
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))

            verify(exactly = 0) { userFacade.authenticate(any(), any()) }
        }

        @DisplayName("UserFacade.authenticate 가 UNAUTHORIZED 를 던지면, 401 응답이 반환된다.")
        @Test
        fun returnsUnauthorized_whenAuthenticationFails() {
            // give
            every {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, "Wrong1234!")
            } throws CoreException(UserErrorType.UNAUTHORIZED)

            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, "Wrong1234!"),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }

        @DisplayName("인증에 성공하면, 핸들러로 통과하고 @LoginUser 로 인증 회원의 AuthUser(loginId, id) 가 주입된다.")
        @Test
        fun passesThroughAndInjectsAuthUser_whenAuthenticationSucceeds() {
            // give
            every {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            } returns mockk<User> {
                every { id } returns 42L
                every { loginId } returns UserFixture.DEFAULT_LOGIN_ID
            }

            // when / then
            mockMvc.perform(
                get("/test/protected")
                    .header(AuthInterceptor.HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
                    .header(AuthInterceptor.HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"ok:${UserFixture.DEFAULT_LOGIN_ID}:42\""))

            verify(exactly = 1) {
                userFacade.authenticate(UserFixture.DEFAULT_LOGIN_ID, UserFixture.DEFAULT_PASSWORD)
            }
        }
    }
}
