package com.loopers.interfaces.api.user

import com.loopers.application.user.UserFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserV1Controller(
    private val userFacade: UserFacade,
) : UserV1ApiSpec {
    @PostMapping
    override fun signup(
        @RequestBody request: UserV1Dto.SignupRequest,
    ): ApiResponse<Any> {
        userFacade.signup(request.toCommand())
        return ApiResponse.success()
    }

    @GetMapping("/me")
    @RequireAuth
    override fun getMyInfo(
        @LoginUser user: AuthUser,
    ): ApiResponse<UserV1Dto.MyInfoResponse> {
        return userFacade.getMyInfo(user.loginId)
            .let { UserV1Dto.MyInfoResponse.from(it) }
            .let { ApiResponse.success(it) }
    }

    @PatchMapping("/me/password")
    @RequireAuth
    override fun changePassword(
        @LoginUser user: AuthUser,
        @RequestBody request: UserV1Dto.ChangePasswordRequest,
    ): ApiResponse<Any> {
        userFacade.changePassword(request.toCommand(user.loginId))
        return ApiResponse.success()
    }
}
