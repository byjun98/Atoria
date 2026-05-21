package com.ssafy.culture.data.user

import com.ssafy.culture.data.remote.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PUT

interface UserApi {
    @GET("users/me")
    suspend fun getMyPage(): ApiResponse<UserProfileDto>

    @PUT("users/me")
    suspend fun updateMyPage(
        @Body request: UpdateUserProfileRequest,
    ): ApiResponse<Unit>

    @PUT("users/me/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest,
    ): ApiResponse<Unit>

    @HTTP(method = "DELETE", path = "users/me", hasBody = true)
    suspend fun deleteAccount(
        @Body request: DeleteAccountRequest,
    ): ApiResponse<Unit>
}
