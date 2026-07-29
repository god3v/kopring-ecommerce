package com.loopers.infrastructure.user

import com.loopers.config.jpa.DataSourceConfig
import com.loopers.domain.user.UserFixture
import com.loopers.testcontainers.MySqlTestContainersConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(MySqlTestContainersConfig::class, DataSourceConfig::class)
class UserJpaRepositoryIntegrationTest @Autowired constructor(
    private val userJpaRepository: UserJpaRepository,
    private val testEntityManager: TestEntityManager,
) {
    private fun persistDefaultUser(): UserEntity {
        val saved = userJpaRepository.save(UserEntity.from(UserFixture.validUser()))
        testEntityManager.flush()
        testEntityManager.clear()
        return saved
    }

    @DisplayName("UserEntity 를 저장할 때, ")
    @Nested
    inner class Save {
        @DisplayName("신규 저장하면, id 가 발급되고 createdAt/updatedAt 이 자동 설정된다.")
        @Test
        fun assignsIdAndTimestamps_whenSavingNewEntity() {
            // when
            val saved = persistDefaultUser()
            val reloaded = userJpaRepository.findById(saved.id).orElseThrow()

            // then
            assertAll(
                { assertThat(reloaded.id).isPositive() },
                { assertThat(reloaded.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
                { assertThat(reloaded.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
                { assertThat(reloaded.name).isEqualTo(UserFixture.DEFAULT_NAME) },
                { assertThat(reloaded.birthDate).isEqualTo(UserFixture.DEFAULT_BIRTH_DATE) },
                { assertThat(reloaded.createdAt).isNotNull() },
                { assertThat(reloaded.updatedAt).isNotNull() },
                { assertThat(reloaded.deletedAt).isNull() },
            )
        }
    }

    @DisplayName("findByLoginId 를 호출할 때, ")
    @Nested
    inner class FindByLoginId {
        @DisplayName("존재하는 loginId 면, 해당 UserEntity 를 반환한다.")
        @Test
        fun returnsEntity_whenLoginIdExists() {
            // give
            val saved = persistDefaultUser()

            // when
            val found = userJpaRepository.findByLoginId(UserFixture.DEFAULT_LOGIN_ID)

            // then
            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected UserEntity but was null after save" }
            assertAll(
                { assertThat(verifiedFound.id).isEqualTo(saved.id) },
                { assertThat(verifiedFound.loginId).isEqualTo(UserFixture.DEFAULT_LOGIN_ID) },
            )
        }

        @DisplayName("존재하지 않는 loginId 면, null 을 반환한다.")
        @Test
        fun returnsNull_whenLoginIdNotFound() {
            // give
            persistDefaultUser()

            // when
            val found = userJpaRepository.findByLoginId("notexists")

            // then
            assertThat(found).isNull()
        }
    }

    @DisplayName("findByEmail 을 호출할 때, ")
    @Nested
    inner class FindByEmail {
        @DisplayName("존재하는 email 이면, 해당 UserEntity 를 반환한다.")
        @Test
        fun returnsEntity_whenEmailExists() {
            // give
            val saved = persistDefaultUser()

            // when
            val found = userJpaRepository.findByEmail(UserFixture.DEFAULT_EMAIL)

            // then
            assertThat(found).isNotNull()
            val verifiedFound = requireNotNull(found) { "expected UserEntity but was null after save" }
            assertAll(
                { assertThat(verifiedFound.id).isEqualTo(saved.id) },
                { assertThat(verifiedFound.email).isEqualTo(UserFixture.DEFAULT_EMAIL) },
            )
        }

        @DisplayName("존재하지 않는 email 이면, null 을 반환한다.")
        @Test
        fun returnsNull_whenEmailNotFound() {
            // give
            persistDefaultUser()

            // when
            val found = userJpaRepository.findByEmail("notexists@example.com")

            // then
            assertThat(found).isNull()
        }
    }
}
