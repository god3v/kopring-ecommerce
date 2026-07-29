package com.loopers.infrastructure.user

import com.loopers.domain.user.UserRepository
import com.loopers.domain.user.User
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component

@Component
class UserRepositoryImpl(
    private val userJpaRepository: UserJpaRepository,
) : UserRepository {
    override fun save(user: User): User =
        userJpaRepository.save(UserEntity.from(user)).toDomain()

    override fun update(user: User): User {
        val entity = userJpaRepository.findById(user.id)
            .orElseThrow { CoreException(UserErrorType.UNAUTHORIZED) }
            .apply { syncFrom(user) }
        return userJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): User? =
        userJpaRepository.findById(id).orElse(null)?.toDomain()

    override fun findAllByIds(ids: Collection<Long>): List<User> =
        if (ids.isEmpty()) emptyList() else userJpaRepository.findAllById(ids).map { it.toDomain() }

    override fun findByLoginId(loginId: String): User? =
        userJpaRepository.findByLoginId(loginId)?.toDomain()

    override fun findByEmail(email: String): User? =
        userJpaRepository.findByEmail(email)?.toDomain()
}
