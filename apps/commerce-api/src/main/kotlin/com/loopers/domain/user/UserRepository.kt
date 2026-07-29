package com.loopers.domain.user

interface UserRepository {
    fun save(user: User): User
    fun update(user: User): User
    fun findById(id: Long): User?
    fun findAllByIds(ids: Collection<Long>): List<User>
    fun findByLoginId(loginId: String): User?
    fun findByEmail(email: String): User?
}
