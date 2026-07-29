package com.loopers.interfaces.api.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.loopers.interfaces.api.ApiControllerAdvice
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

class AdminAuthInterceptorTest {
    private val expectedLdap = "loopers.admin"
    private val interceptor = AdminAuthInterceptor(expectedLdap)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(StubController())
        .addInterceptors(interceptor)
        .setControllerAdvice(ApiControllerAdvice())
        .setMessageConverters(MappingJackson2HttpMessageConverter(jacksonObjectMapper()))
        .build()

    @RestController
    class StubController {
        @GetMapping("/api-admin/test")
        fun endpoint(): String = "admin-ok"
    }

    @DisplayName("X-Loopers-Ldap 헤더로 관리자 인증을 할 때, ")
    @Nested
    inner class AdminAuth {
        @DisplayName("값이 기대값(loopers.admin)과 일치하면 핸들러로 통과한다.")
        @Test
        fun passes_whenLdapMatches() {
            mockMvc.perform(
                get("/api-admin/test").header(AdminAuthInterceptor.HEADER_ADMIN_LDAP, "loopers.admin"),
            )
                .andExpect(status().isOk)
                .andExpect(content().string("\"admin-ok\""))
        }

        @DisplayName("헤더가 누락되면 401 UNAUTHORIZED 응답이 반환된다.")
        @Test
        fun unauthorized_whenHeaderMissing() {
            mockMvc.perform(get("/api-admin/test"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }

        @DisplayName("값이 기대값과 다르면 401 UNAUTHORIZED 응답이 반환된다.")
        @Test
        fun unauthorized_whenLdapMismatch() {
            mockMvc.perform(
                get("/api-admin/test").header(AdminAuthInterceptor.HEADER_ADMIN_LDAP, "someone.else"),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.meta.errorCode", equalTo("UNAUTHORIZED")))
        }
    }
}
