package com.loopers.application.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.domain.user.PasswordEncryptionUtil
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture.DEFAULT_BIRTH_DATE
import com.loopers.domain.user.UserFixture.DEFAULT_EMAIL
import com.loopers.domain.user.UserFixture.DEFAULT_LOGIN_ID
import com.loopers.domain.user.UserFixture.DEFAULT_NAME
import com.loopers.domain.user.UserFixture.DEFAULT_PASSWORD
import com.loopers.infrastructure.user.UserJpaRepository
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorStatus
import com.loopers.utils.DatabaseCleanUp
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException

@SpringBootTest
class UserFacadeIntegrationTest @Autowired constructor(
    private val userFacade: UserFacade,
    private val userJpaRepository: UserJpaRepository,
    private val databaseCleanUp: DatabaseCleanUp,
) {
    @AfterEach
    fun tearDown() {
        databaseCleanUp.truncateAllTables()
    }

    private fun validSignupCommand(
        loginId: String = DEFAULT_LOGIN_ID,
        email: String = DEFAULT_EMAIL,
    ): SignupCommand = SignupCommand(
        loginId = loginId,
        password = DEFAULT_PASSWORD,
        name = DEFAULT_NAME,
        birthDate = DEFAULT_BIRTH_DATE,
        email = email,
    )

    @DisplayName("UserFacade.signup 을 호출할 때, ")
    @Nested
    inner class Signup {
        @DisplayName("정상 입력으로 호출하면, SignupResult 가 반환되고 users 테이블에 1 row 가 영속된다.")
        @Test
        fun returnsSignupResultAndPersistsRow_whenValidInput() {
            // when
            val result = userFacade.signup(validSignupCommand())

            // then
            assertAll(
                { assertThat(result.id).isPositive() },
                { assertThat(result.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }

        @DisplayName("도메인 검증 실패로 예외가 던져지면, 트랜잭션이 롤백되어 DB 에 부분 저장이 없다.")
        @Test
        fun rollsBackAndPersistsNothing_whenDomainExceptionThrown() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.signup(validSignupCommand(loginId = "한글포함"))
            }

            // then
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.SIGNUP_BAD_REQUEST) },
                { assertThat(userJpaRepository.count()).isEqualTo(0L) },
            )
        }

        @DisplayName("이미 가입된 loginId 로 호출하면, DUPLICATE_LOGIN_ID 예외가 발생하고 두 번째 row 는 영속되지 않는다.")
        @Test
        fun rollsBackOnDuplicateLoginId() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.signup(validSignupCommand(email = "other@example.com"))
            }

            // then
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_LOGIN_ID) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }

        @DisplayName("이미 가입된 email 로 호출하면, DUPLICATE_EMAIL 예외가 발생하고 두 번째 row 는 영속되지 않는다.")
        @Test
        fun rollsBackOnDuplicateEmail() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.signup(validSignupCommand(loginId = "another"))
            }

            // then
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.DUPLICATE_EMAIL) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }

        @DisplayName("동일 loginId/email 로 동시에 signup 하면, 정확히 1건만 성공하고 나머지는 중복으로 거부되며 row 는 1건만 남는다.")
        @Test
        fun onlyOneSucceeds_whenConcurrentDuplicateSignup() {
            // give
            val threadCount = 8
            val executor = Executors.newFixedThreadPool(threadCount)
            val ready = CountDownLatch(threadCount)
            val start = CountDownLatch(1)
            val successes = AtomicInteger(0)
            val rejections = AtomicInteger(0)
            val unexpected = CopyOnWriteArrayList<Throwable>()

            // when — 모든 스레드를 장벽에서 동시에 출발시켜 선조회~저장 사이 경합을 최대화한다.
            // 거부 경로는 둘 중 하나다: 선조회가 먼저 잡으면 CoreException(DUPLICATE_*, 409),
            // 선조회를 통과한 경합은 커밋 시 DB 유니크 제약 → DataIntegrityViolationException.
            val futures = (1..threadCount).map {
                executor.submit {
                    ready.countDown()
                    start.await()
                    try {
                        userFacade.signup(validSignupCommand())
                        successes.incrementAndGet()
                    } catch (e: CoreException) {
                        if (e.errorType.status == ErrorStatus.CONFLICT) rejections.incrementAndGet() else unexpected.add(e)
                    } catch (e: DataIntegrityViolationException) {
                        rejections.incrementAndGet()
                    } catch (e: Throwable) {
                        unexpected.add(e)
                    }
                }
            }
            ready.await()
            start.countDown()
            futures.forEach { it.get(10, TimeUnit.SECONDS) }
            executor.shutdown()

            // then
            assertAll(
                { assertThat(successes.get()).isEqualTo(1) },
                { assertThat(rejections.get()).isEqualTo(threadCount - 1) },
                { assertThat(unexpected).isEmpty() },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }
    }

    @DisplayName("UserFacade.authenticate 를 호출할 때, ")
    @Nested
    inner class Authenticate {
        @DisplayName("loginId 와 비밀번호가 모두 일치하면, 식별된 User 가 반환된다.")
        @Test
        fun returnsUser_whenCredentialsMatch() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val authenticated = userFacade.authenticate(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD)

            // then
            assertThat(authenticated.loginId).isEqualTo(DEFAULT_LOGIN_ID)
        }

        @DisplayName("존재하지 않는 loginId 로 인증을 시도하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenLoginIdNotFound() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.authenticate(DEFAULT_LOGIN_ID, DEFAULT_PASSWORD)
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }

        @DisplayName("loginId 는 존재하지만 비밀번호가 일치하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenPasswordMismatch() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.authenticate(DEFAULT_LOGIN_ID, "Wrong1234!")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("UserFacade.getMyInfo 를 호출할 때, ")
    @Nested
    inner class GetMyInfo {
        @DisplayName("가입된 loginId 로 호출하면, 마스킹된 name 을 포함한 MyInfoResult 가 반환된다.")
        @Test
        fun returnsMaskedMyInfo_whenUserExists() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = userFacade.getMyInfo(DEFAULT_LOGIN_ID)

            // then
            assertAll(
                { assertThat(result.loginId).isEqualTo(DEFAULT_LOGIN_ID) },
                { assertThat(result.name).contains("*") },
                { assertThat(result.email).isEqualTo(DEFAULT_EMAIL) },
                { assertThat(result.birthDate).isEqualTo(DEFAULT_BIRTH_DATE) },
            )
        }

        @DisplayName("존재하지 않는 loginId 로 호출하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUserNotFound() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.getMyInfo("notexists")
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("UserFacade.changePassword 를 호출할 때, ")
    @Nested
    inner class ChangePassword {
        private val newPassword = "NewPw5678!"

        @DisplayName("정상 입력으로 호출하면, DB 의 password 가 새 값으로 갱신된다.")
        @Test
        fun updatesPasswordInDb_whenValidInput() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            userFacade.changePassword(
                ChangePasswordCommand(
                    loginId = DEFAULT_LOGIN_ID,
                    prevPw = DEFAULT_PASSWORD,
                    nextPw = newPassword,
                ),
            )

            // then
            val reloaded = requireNotNull(userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)) {
                "user should exist after signup"
            }
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(newPassword) },
                { assertThat(reloaded.password).isEqualTo(PasswordEncryptionUtil.encode(newPassword)) },
            )
        }

        @DisplayName("RULE 위반 예외가 던져지면, 트랜잭션이 롤백되어 DB 의 password 가 변경되지 않는다.")
        @Test
        fun rollsBackPasswordChange_whenRuleViolated() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = DEFAULT_LOGIN_ID,
                            prevPw = DEFAULT_PASSWORD,
                        nextPw = "short1!",
                    ),
                )
            }

            // then
            val reloaded = requireNotNull(userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)) {
                "user should exist after signup"
            }
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(DEFAULT_PASSWORD) },
                { assertThat(reloaded.password).isEqualTo(PasswordEncryptionUtil.encode(DEFAULT_PASSWORD)) },
            )
        }

        @DisplayName("loginId 에 해당하는 User 가 존재하지 않으면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUserNotFound() {
            // when
            val result = assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = "notexists",
                        prevPw = DEFAULT_PASSWORD,
                        nextPw = newPassword,
                    ),
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, PASSWORD_CHANGE_BAD_REQUEST 예외가 발생한다.")
        @Test
        fun throwsPasswordChangeBadRequest_whenNextPwEqualsPrev() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = DEFAULT_LOGIN_ID,
                        prevPw = DEFAULT_PASSWORD,
                        nextPw = DEFAULT_PASSWORD,
                    ),
                )
            }

            // then
            assertThat(result.errorType).isEqualTo(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST)
        }

        @DisplayName("body 의 prevPw 가 일치하지 않으면, UNAUTHORIZED 예외가 발생하고 password 가 변경되지 않는다.")
        @Test
        fun throwsUnauthorizedAndRollsBack_whenPrevPwMismatch() {
            // give
            userFacade.signup(validSignupCommand())

            // when
            val result = assertThrows<CoreException> {
                userFacade.changePassword(
                    ChangePasswordCommand(
                        loginId = DEFAULT_LOGIN_ID,
                            prevPw = "Wrong1234!",
                        nextPw = newPassword,
                    ),
                )
            }

            // then
            val reloaded = requireNotNull(userJpaRepository.findByLoginId(DEFAULT_LOGIN_ID)) {
                "user should exist after signup"
            }
            assertAll(
                { assertThat(result.errorType).isEqualTo(UserErrorType.UNAUTHORIZED) },
                { assertThat(reloaded.password).isNotEqualTo(DEFAULT_PASSWORD) },
                { assertThat(reloaded.password).isEqualTo(PasswordEncryptionUtil.encode(DEFAULT_PASSWORD)) },
            )
        }
    }
}
