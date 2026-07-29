package com.loopers.interfaces.api.user

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.application.user.UserFacade
import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.application.user.result.MyInfoResult
import com.loopers.application.user.result.SignupResult
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.interfaces.api.ApiControllerAdvice
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.support.error.CoreException
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class UserV1ControllerTest {
    private val userFacade: UserFacade = mockk()
    private val controller = UserV1Controller(userFacade)
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    // 컨트롤러 단위 테스트는 인증을 가정한 상태에서 매핑/위임만 검증한다. 인증 분기 검증은 AuthInterceptorTest 가 담당한다.
    private val stubLoginUserResolver = object : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter): Boolean =
            parameter.hasParameterAnnotation(LoginUser::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: WebDataBinderFactory?,
        ): Any = AuthUser(id = 1L, loginId = UserFixture.DEFAULT_LOGIN_ID)
    }

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(ApiControllerAdvice())
        .setCustomArgumentResolvers(stubLoginUserResolver)
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"
        private const val ENDPOINT_ME = "/api/v1/users/me"
        private const val ENDPOINT_PASSWORD = "/api/v1/users/me/password"

        private fun validSignupRequest(
            loginId: String = UserFixture.DEFAULT_LOGIN_ID,
            password: String = UserFixture.DEFAULT_PASSWORD,
            name: String = UserFixture.DEFAULT_NAME,
        ): UserV1Dto.SignupRequest = UserV1Dto.SignupRequest(
            loginId = loginId,
            password = password,
            name = name,
            birthDate = UserFixture.DEFAULT_BIRTH_DATE,
            email = UserFixture.DEFAULT_EMAIL,
        )
    }

    @DisplayName("POST /api/v1/users 회원가입을 호출할 때, ")
    @Nested
    inner class Signup {
        @DisplayName("유효한 본문으로 호출하면, 200 success 응답을 반환하고 본문이 SignupCommand 로 매핑되어 Facade 로 전달된다.")
        @Test
        fun returnsSuccessAndMapsToCommand_whenValidBody() {
            // give
            val captured = slot<SignupCommand>()
            every { userFacade.signup(capture(captured)) } returns SignupResult(1L, UserFixture.DEFAULT_LOGIN_ID)

            // when / then
            mockMvc.perform(
                post(ENDPOINT_SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSignupRequest())),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.meta.result", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.data").doesNotExist())

            verify(exactly = 1) { userFacade.signup(any()) }
            assertThat(captured.captured).isEqualTo(
                SignupCommand(
                    loginId = UserFixture.DEFAULT_LOGIN_ID,
                    password = UserFixture.DEFAULT_PASSWORD,
                    name = UserFixture.DEFAULT_NAME,
                    birthDate = UserFixture.DEFAULT_BIRTH_DATE,
                    email = UserFixture.DEFAULT_EMAIL,
                ),
            )
        }

        @DisplayName("Facade 가 SIGNUP_BAD_REQUEST 를 던지면, 400 응답과 errorCode 가 반환된다.")
        @Test
        fun returnsBadRequest_whenFacadeThrowsSignupBadRequest() {
            // give
            every { userFacade.signup(any()) } throws CoreException(UserErrorType.SIGNUP_BAD_REQUEST)

            // when / then
            mockMvc.perform(
                post(ENDPOINT_SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSignupRequest())),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("SIGNUP_BAD_REQUEST")))
        }

        @DisplayName("Facade 가 DUPLICATE_LOGIN_ID 를 던지면, 409 응답과 errorCode 가 반환된다.")
        @Test
        fun returnsConflict_whenFacadeThrowsDuplicateLoginId() {
            // give
            every { userFacade.signup(any()) } throws CoreException(UserErrorType.DUPLICATE_LOGIN_ID)

            // when / then
            mockMvc.perform(
                post(ENDPOINT_SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validSignupRequest())),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("DUPLICATE_LOGIN_ID")))
        }

        @DisplayName("필수 필드가 누락된 본문으로 호출하면, 400 BAD_REQUEST 응답이 반환되고 Facade 는 호출되지 않는다.")
        @Test
        fun returnsBadRequest_whenRequiredFieldIsMissing() {
            // when / then
            mockMvc.perform(
                post(ENDPOINT_SIGNUP)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"loginId":"goryeojin"}"""),
            )
                .andExpect(status().isBadRequest)

            verify(exactly = 0) { userFacade.signup(any()) }
        }
    }

    @DisplayName("GET /api/v1/users/me 내 정보를 조회할 때, ")
    @Nested
    inner class GetMyInfo {
        @DisplayName("인증된 상태로 호출하면, Facade 가 LoginUser 로 해석된 loginId 로 호출되고 MyInfoResponse 가 반환된다.")
        @Test
        fun returnsMyInfoResponse_whenAuthenticated() {
            // give
            every { userFacade.getMyInfo(UserFixture.DEFAULT_LOGIN_ID) } returns MyInfoResult(
                loginId = UserFixture.DEFAULT_LOGIN_ID,
                name = "고려*",
                birthDate = UserFixture.DEFAULT_BIRTH_DATE,
                email = UserFixture.DEFAULT_EMAIL,
            )

            // when / then
            mockMvc.perform(get(ENDPOINT_ME))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.meta.result", equalTo("SUCCESS")))
                .andExpect(jsonPath("$.data.loginId", equalTo(UserFixture.DEFAULT_LOGIN_ID)))
                .andExpect(jsonPath("$.data.name", equalTo("고려*")))
                .andExpect(jsonPath("$.data.email", equalTo(UserFixture.DEFAULT_EMAIL)))
                .andExpect(jsonPath("$.data.birthDate", equalTo(UserFixture.DEFAULT_BIRTH_DATE.toString())))

            verify(exactly = 1) { userFacade.getMyInfo(UserFixture.DEFAULT_LOGIN_ID) }
        }

        @DisplayName("Facade 가 UNAUTHORIZED 를 던지면, 401 응답이 반환된다.")
        @Test
        fun returnsUnauthorized_whenFacadeThrowsUnauthorized() {
            // give
            every { userFacade.getMyInfo(any()) } throws CoreException(UserErrorType.UNAUTHORIZED)

            // when / then
            mockMvc.perform(get(ENDPOINT_ME))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }
    }

    @DisplayName("PATCH /api/v1/users/me/password 비밀번호를 변경할 때, ")
    @Nested
    inner class ChangePassword {
        private val nextPw = "NewPw5678!"

        @DisplayName("인증된 상태에서 정상 본문으로 호출하면, 200 success 응답을 반환하고 LoginUser + 본문이 ChangePasswordCommand 로 매핑된다.")
        @Test
        fun returnsSuccessAndMapsToCommand_whenValidRequest() {
            // give
            val captured = slot<ChangePasswordCommand>()
            every { userFacade.changePassword(capture(captured)) } returns Unit
            val body = UserV1Dto.ChangePasswordRequest(prevPw = UserFixture.DEFAULT_PASSWORD, nextPw = nextPw)

            // when / then
            mockMvc.perform(
                patch(ENDPOINT_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(body)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.meta.result", equalTo("SUCCESS")))

            assertThat(captured.captured).isEqualTo(
                ChangePasswordCommand(
                    loginId = UserFixture.DEFAULT_LOGIN_ID,
                    prevPw = UserFixture.DEFAULT_PASSWORD,
                    nextPw = nextPw,
                ),
            )
        }

        @DisplayName("Facade 가 INVALID_PASSWORD 를 던지면, 400 응답과 errorCode 가 반환된다.")
        @Test
        fun returnsBadRequest_whenFacadeThrowsInvalidPassword() {
            // give
            every { userFacade.changePassword(any()) } throws CoreException(UserErrorType.INVALID_PASSWORD)

            // when / then
            mockMvc.perform(
                patch(ENDPOINT_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            UserV1Dto.ChangePasswordRequest(prevPw = UserFixture.DEFAULT_PASSWORD, nextPw = "short1!"),
                        ),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("INVALID_PASSWORD")))
        }

        @DisplayName("Facade 가 PASSWORD_CHANGE_BAD_REQUEST 를 던지면, 400 응답과 errorCode 가 반환된다.")
        @Test
        fun returnsBadRequest_whenFacadeThrowsPasswordChangeBadRequest() {
            // give
            every {
                userFacade.changePassword(any())
            } throws CoreException(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST)

            // when / then
            mockMvc.perform(
                patch(ENDPOINT_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            UserV1Dto.ChangePasswordRequest(
                                prevPw = UserFixture.DEFAULT_PASSWORD,
                                nextPw = UserFixture.DEFAULT_PASSWORD,
                            ),
                        ),
                    ),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("PASSWORD_CHANGE_BAD_REQUEST")))
        }

        @DisplayName("Facade 가 UNAUTHORIZED 를 던지면, 401 응답과 errorCode 가 반환된다.")
        @Test
        fun returnsUnauthorized_whenFacadeThrowsUnauthorized() {
            // give
            every { userFacade.changePassword(any()) } throws CoreException(UserErrorType.UNAUTHORIZED)

            // when / then
            mockMvc.perform(
                patch(ENDPOINT_PASSWORD)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            UserV1Dto.ChangePasswordRequest(prevPw = "Wrong1234!", nextPw = nextPw),
                        ),
                    ),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }
    }
}
