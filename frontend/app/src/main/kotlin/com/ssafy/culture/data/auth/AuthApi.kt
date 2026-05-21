package com.ssafy.culture.data.auth

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.Response

interface AuthApi {
    @GET("oauth2/authorization/{provider}")
    suspend fun getOAuthAuthorization(
        @Path("provider") provider: String,
    ): AuthApiResponseDto<OAuthAuthorizationDataDto>

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequestDto,
    ): AuthApiResponseDto<LoginDataDto>

    @POST("auth/signup")
    suspend fun signup(
        @Body request: SignupRequestDto,
    ): Response<AuthApiResponseDto<SignupDataDto>>

    @POST("auth/email/send-code")
    suspend fun sendEmailCode(
        @Body request: EmailRequestDto,
    ): Response<AuthApiResponseDto<Unit>>

    @POST("auth/email/verify-code")
    suspend fun verifyEmailCode(
        @Body request: VerifyEmailCodeRequestDto,
    ): Response<AuthApiResponseDto<EmailVerificationDataDto>>

    @GET("auth/email/exists")
    suspend fun checkEmailAvailability(
        @Query("email") email: String,
    ): AuthApiResponseDto<AvailabilityDataDto>

    @GET("auth/nickname/exists")
    suspend fun checkNicknameAvailability(
        @Query("nickname") nickname: String,
    ): AuthApiResponseDto<AvailabilityDataDto>

    @POST("auth/logout")
    suspend fun logout(
        @Body request: TokenRequestDto,
    ): AuthApiResponseDto<Unit>

    @POST("auth/token/refresh")
    suspend fun refreshToken(
        @Body request: TokenRequestDto,
    ): AuthApiResponseDto<TokenRefreshDataDto>
}
