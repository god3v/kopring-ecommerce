package com.loopers.infrastructure.user

import com.loopers.domain.user.UserRepository
import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.user.Password
import com.loopers.domain.user.PasswordEncryptionUtil
import com.loopers.domain.user.User
import com.loopers.domain.user.UserErrorType
import com.loopers.domain.user.UserFixture
import com.loopers.support.error.CoreException
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class, UserRepositoryImpl::class)
class UserRepositoryImplIntegrationTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val userJpaRepository: UserJpaRepository,
    private val testEntityManager: TestEntityManager,
) {
    @DisplayName("save 를 호출할 때, ")
    @Nested
    inner class Save {
        @DisplayName("id 가 0L 인 신규 User 를 저장하면, 새 row 가 영속되고 발급된 id 를 가진 User 가 반환된다.")
        @Test
        fun persistsNewRow_whenIdIsZero() {
            // give
            val user = UserFixture.validUser()

            // when
            val saved = userRepository.save(user)
            testEntityManager.flush()
            testEntityManager.clear()

            // then
            val reloaded = userJpaRepository.findById(saved.id).orElseThrow()
            assertAll(
                { assertThat(saved.id).isPositive() },
                { assertThat(reloaded.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(reloaded.password).isNotEqualTo(UserFixture.DEFAULT_PASSWORD) },
                { assertThat(reloaded.password).isEqualTo(PasswordEncryptionUtil.encode(UserFixture.DEFAULT_PASSWORD)) },
                { assertThat(reloaded.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
                { assertThat(reloaded.name).isEqualTo(UserFixture.DEFAULT_NAME) },
                { assertThat(reloaded.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
            )
        }
    }

    @DisplayName("update 를 호출할 때, ")
    @Nested
    inner class Update {
        @DisplayName("기존 User 를 update 하면, syncFrom 으로 password 만 갱신되고 다른 컬럼은 유지된다.")
        @Test
        fun updatesOnlyPassword_whenIdExists() {
            // give
            val newPassword = "NewPw5678!"
            val saved = userRepository.save(UserFixture.validUser())
            testEntityManager.flush()
            testEntityManager.clear()
            saved.changePassword(Password.create(newPassword, saved.birthDate))

            // when
            userRepository.update(saved)
            testEntityManager.flush()
            testEntityManager.clear()

            // then
            val reloaded = userJpaRepository.findById(saved.id).orElseThrow()
            assertAll(
                { assertThat(reloaded.password).isNotEqualTo(newPassword) },
                { assertThat(reloaded.password).isEqualTo(PasswordEncryptionUtil.encode(newPassword)) },
                { assertThat(reloaded.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(reloaded.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
                { assertThat(reloaded.name).isEqualTo(UserFixture.DEFAULT_NAME) },
                { assertThat(reloaded.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
                { assertThat(userJpaRepository.count()).isEqualTo(1L) },
            )
        }

        @DisplayName("DB 에 존재하지 않는 id 로 update 하면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        fun throwsUnauthorized_whenUpdatingNonExistentId() {
            // give
            val template = UserFixture.validUser()
            val ghost = User(
                id = 999L,
                loginId = template.loginId,
                password = template.password,
                name = template.name,
                birthDate = template.birthDate,
                email = template.email,
            )

            // when / then
            val ex = assertThrows<CoreException> {
                userRepository.update(ghost)
            }
            assertThat(ex.errorType).isEqualTo(UserErrorType.UNAUTHORIZED)
        }
    }

    @DisplayName("findByLoginId 를 호출할 때, ")
    @Nested
    inner class FindByLoginId {
        @DisplayName("존재하는 loginId 면, 도메인 User 로 변환되어 반환된다.")
        @Test
        fun returnsDomainUser_whenLoginIdExists() {
            // give
            val saved = userRepository.save(UserFixture.validUser())
            testEntityManager.flush()
            testEntityManager.clear()

            // when
            val found = userRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)

            // then
            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected User but was null after save" }
            assertAll(
                { assertThat(verifiedFound.id).isEqualTo(saved.id) },
                { assertThat(verifiedFound.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(verifiedFound.email.value).isEqualTo(UserFixture.DEFAULT_EMAIL) },
                { assertThat(verifiedFound.password.matches(UserFixture.DEFAULT_PASSWORD)).isTrue() },
            )
        }

        @DisplayName("존재하지 않는 loginId 면, null 을 반환한다.")
        @Test
        fun returnsNull_whenLoginIdNotFound() {
            // when
            val found = userRepository.findByLoginId("notexists")

            // then
            assertThat(found).isNull()
        }
    }

    @DisplayName("findByEmail 을 호출할 때, ")
    @Nested
    inner class FindByEmail {
        @DisplayName("존재하는 email 이면, 도메인 User 로 변환되어 반환된다.")
        @Test
        fun returnsDomainUser_whenEmailExists() {
            // give
            val saved = userRepository.save(UserFixture.validUser())
            testEntityManager.flush()
            testEntityManager.clear()

            // when
            val found = userRepository.findByEmail(UserFixture.DEFAULT_EMAIL)

            // then
            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected User but was null after save" }
            assertAll(
                { assertThat(verifiedFound.id).isEqualTo(saved.id) },
                { assertThat(verifiedFound.email.value).isEqualTo(UserFixture.DEFAULT_EMAIL) },
            )
        }

        @DisplayName("존재하지 않는 email 이면, null 을 반환한다.")
        @Test
        fun returnsNull_whenEmailNotFound() {
            // when
            val found = userRepository.findByEmail("notexists@example.com")

            // then
            assertThat(found).isNull()
        }
    }
}
