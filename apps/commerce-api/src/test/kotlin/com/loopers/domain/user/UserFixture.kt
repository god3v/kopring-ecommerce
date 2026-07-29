package com.loopers.domain.user

import java.time.LocalDate

object UserFixture {
    const val DEFAULT_LOGIN_ID = "goryeojin"
    const val DEFAULT_PASSWORD = "Asdf1234!"
    const val DEFAULT_NAME = "고려진"
    val DEFAULT_BIRTH_DATE: LocalDate = LocalDate.of(2001, 7, 9)
    const val DEFAULT_EMAIL = "goryeojin@example.com"

    fun validUser(
        loginId: String = DEFAULT_LOGIN_ID,
        password: String = DEFAULT_PASSWORD,
        name: String = DEFAULT_NAME,
        birthDate: LocalDate = DEFAULT_BIRTH_DATE,
        email: String = DEFAULT_EMAIL,
        id: Long = 0L,
    ): User = User(
        id = id,
        loginId = loginId,
        password = Password.create(password, birthDate),
        name = name,
        birthDate = birthDate,
        email = Email.of(email),
    )
}
