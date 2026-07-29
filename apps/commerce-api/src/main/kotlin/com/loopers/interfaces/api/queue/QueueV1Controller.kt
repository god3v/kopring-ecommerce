package com.loopers.interfaces.api.queue

import com.loopers.application.queue.QueueFacade
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/queue")
class QueueV1Controller(
    private val queueFacade: QueueFacade,
) : QueueV1ApiSpec {
    @PostMapping("/enter")
    @RequireAuth
    override fun enter(
        @LoginUser user: AuthUser,
    ): ApiResponse<QueueV1Dto.PositionResponse> {
        val result = queueFacade.enter(user.id)
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(result))
    }

    @GetMapping("/position")
    @RequireAuth
    override fun position(
        @LoginUser user: AuthUser,
    ): ApiResponse<QueueV1Dto.PositionResponse> {
        val result = queueFacade.position(user.id)
        return ApiResponse.success(QueueV1Dto.PositionResponse.from(result))
    }
}
