package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.application.like.query.GetMyLikesQuery
import com.loopers.interfaces.api.ApiResponse
import com.loopers.interfaces.api.auth.AuthUser
import com.loopers.interfaces.api.auth.LoginUser
import com.loopers.interfaces.api.auth.RequireAuth
import com.loopers.support.page.PageQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class LikeV1Controller(
    private val likeFacade: LikeFacade,
) : LikeV1ApiSpec {
    @PostMapping("/products/{productId}/likes")
    @RequireAuth
    override fun like(
        @LoginUser user: AuthUser,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(user.id, productId)
        return ApiResponse.success()
    }

    @DeleteMapping("/products/{productId}/likes")
    @RequireAuth
    override fun unlike(
        @LoginUser user: AuthUser,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(user.id, productId)
        return ApiResponse.success()
    }

    @GetMapping("/users/{userId}/likes")
    @RequireAuth
    override fun getMyLikes(
        @LoginUser user: AuthUser,
        @PathVariable userId: Long,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ApiResponse<LikeV1Dto.LikedProductsResponse> {
        val query = GetMyLikesQuery(
            userId = userId,
            paging = PageQuery(page = pageable.pageNumber, size = pageable.pageSize),
        )
        val result = likeFacade.getMyLikes(user.id, query)
        return ApiResponse.success(LikeV1Dto.LikedProductsResponse.from(result))
    }
}
