package com.loopers.interfaces.api

import com.loopers.application.queue.QueueFacade
import com.loopers.domain.user.UserFixture
import com.loopers.interfaces.api.queue.QueueV1Dto
import com.loopers.interfaces.api.user.UserV1Dto
import com.loopers.testcontainers.MySqlTestContainersConfig
import com.loopers.testcontainers.RedisTestContainersConfig
import com.loopers.utils.DatabaseCleanUp
import com.loopers.utils.RedisCleanUp
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

/**
 * 대기열 진입(1-A)·순번 조회(1-B) E2E.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig::class, RedisTestContainersConfig::class)
class QueueV1ApiE2ETest @Autowired constructor(
    private val testRestTemplate: TestRestTemplate,
    private val databaseCleanUp: DatabaseCleanUp,
    private val redisCleanUp: RedisCleanUp,
    private val queueFacade: QueueFacade,
) {
    @BeforeEach
    fun setUp() {
        testRestTemplate.exchange(
            ENDPOINT_SIGNUP,
            HttpMethod.POST,
            HttpEntity(validSignupRequest()),
            object : ParameterizedTypeReference<ApiResponse<Any>>() {},
        )
    }

    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
        redisCleanUp.truncateAll()
    }

    @DisplayName("POST /api/v1/queue/enter — 인증 회원이 진입하면 200 과 순번(0)·전체 인원(1)을 받는다.")
    @Test
    fun returnsPosition_whenEnter() {
        val response = enter()

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.meta?.result).isEqualTo(ApiResponse.Metadata.Result.SUCCESS) },
            { assertThat(response.body?.data?.position).isEqualTo(0L) },
            { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
            { assertThat(response.body?.data?.estimatedWaitSeconds).isEqualTo(0L) },
            { assertThat(response.body?.data?.entryToken).isNull() },
        )
    }

    @DisplayName("재진입해도 순번이 유지되고 대기 인원이 늘지 않는다(멱등).")
    @Test
    fun keepsPosition_whenReenter() {
        enter()

        val response = enter()

        assertAll(
            { assertThat(response.body?.data?.position).isEqualTo(0L) },
            { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
        )
    }

    @DisplayName("GET /api/v1/queue/position — 진입 후 순번을 조회한다.")
    @Test
    fun returnsPosition_whenQuery() {
        enter()

        val response = position()

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.position).isEqualTo(0L) },
            { assertThat(response.body?.data?.totalWaiting).isEqualTo(1L) },
            { assertThat(response.body?.data?.pollIntervalSeconds).isEqualTo(1L) },
        )
    }

    @DisplayName("입장 처리되면 순번 조회 응답에 토큰이 포함되고 순번은 null 이 된다.")
    @Test
    fun includesToken_afterAdmission() {
        enter()
        // 스케줄러는 test 프로필에서 꺼져 있으므로, 입장 처리를 직접 트리거한다.
        queueFacade.admit(1)

        val response = position()

        assertAll(
            { assertThat(response.statusCode).isEqualTo(HttpStatus.OK) },
            { assertThat(response.body?.data?.position).isNull() },
            { assertThat(response.body?.data?.entryToken).isNotNull() },
            { assertThat(response.body?.data?.pollIntervalSeconds).isEqualTo(0L) },
        )
    }

    @DisplayName("인증 없이 진입하면 401 을 받는다.")
    @Test
    fun returnsUnauthorized_whenNoAuth() {
        val response = testRestTemplate.exchange(
            "/api/v1/queue/enter",
            HttpMethod.POST,
            HttpEntity<Void>(HttpHeaders()),
            positionResponse(),
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun enter(): ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> =
        testRestTemplate.exchange(
            "/api/v1/queue/enter",
            HttpMethod.POST,
            HttpEntity<Void>(authHeaders()),
            positionResponse(),
        )

    private fun position(): ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> =
        testRestTemplate.exchange(
            "/api/v1/queue/position",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders()),
            positionResponse(),
        )

    private fun authHeaders(): HttpHeaders = HttpHeaders().apply {
        set(HEADER_LOGIN_ID, UserFixture.DEFAULT_LOGIN_ID)
        set(HEADER_LOGIN_PW, UserFixture.DEFAULT_PASSWORD)
    }

    private fun positionResponse() =
        object : ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {}

    companion object {
        private const val ENDPOINT_SIGNUP = "/api/v1/users"
        private const val HEADER_LOGIN_ID = "X-Loopers-LoginId"
        private const val HEADER_LOGIN_PW = "X-Loopers-LoginPw"

        private fun validSignupRequest(): UserV1Dto.SignupRequest = UserV1Dto.SignupRequest(
            loginId = UserFixture.DEFAULT_LOGIN_ID,
            password = UserFixture.DEFAULT_PASSWORD,
            name = UserFixture.DEFAULT_NAME,
            birthDate = UserFixture.DEFAULT_BIRTH_DATE,
            email = UserFixture.DEFAULT_EMAIL,
        )
    }
}
