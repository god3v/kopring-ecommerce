package com.loopers.application.user

import com.loopers.application.user.command.ChangePasswordCommand
import com.loopers.application.user.command.SignupCommand
import com.loopers.domain.user.UserRepository
import com.loopers.application.user.result.MyInfoResult
import com.loopers.application.user.result.SignupResult
import com.loopers.domain.user.Password
import com.loopers.domain.user.User
import com.loopers.domain.user.UserErrorType
import com.loopers.support.error.CoreException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UserFacade(
    private val userRepository: UserRepository,
) {
    @Transactional
    fun signup(command: SignupCommand): SignupResult {
        if (userRepository.findByLoginId(command.loginId) != null) {
            throw CoreException(UserErrorType.DUPLICATE_LOGIN_ID)
        }
        if (userRepository.findByEmail(command.email) != null) {
            throw CoreException(UserErrorType.DUPLICATE_EMAIL)
        }
        val user = User.signUp(
            loginId = command.loginId,
            password = Password.create(command.password, command.birthDate),
            name = command.name,
            birthDate = command.birthDate,
            email = command.email,
        )
        return SignupResult.from(userRepository.save(user))
    }

    @Transactional(readOnly = true)
    fun getMyInfo(loginId: String): MyInfoResult {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        return MyInfoResult.from(user)
    }

    @Transactional(readOnly = true)
    fun authenticate(loginId: String, plainPassword: String): User {
        val user = userRepository.findByLoginId(loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (!user.password.matches(plainPassword)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        return user
    }

    @Transactional
    fun changePassword(command: ChangePasswordCommand) {
        val user = userRepository.findByLoginId(command.loginId)
            ?: throw CoreException(UserErrorType.UNAUTHORIZED)
        if (!user.password.matches(command.prevPw)) {
            throw CoreException(UserErrorType.UNAUTHORIZED)
        }
        if (command.prevPw == command.nextPw) {
            throw CoreException(UserErrorType.PASSWORD_CHANGE_BAD_REQUEST, "현재 비밀번호와 동일합니다.")
        }
        user.changePassword(Password.create(command.nextPw, user.birthDate))
        userRepository.update(user)
    }
}
