package com.ssafy.culture.data.auth

data class AuthApiResponseDto<T>(
    val success: Boolean,
    val code: Int,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null,
)

data class LoginRequestDto(
    val email: String,
    val password: String,
)

data class LoginDataDto(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUserDto,
)

data class OAuthAuthorizationDataDto(
    val authorizationUrl: String,
)

data class AuthUserDto(
    val userId: Long,
    val nickname: String,
)

data class SignupRequestDto(
    val nickname: String,
    val email: String,
    val authCode: String,
    val password: String,
    val passwordConfirm: String,
)

data class SignupDataDto(
    val userId: Long,
    val email: String,
    val nickname: String,
)

data class EmailRequestDto(
    val email: String,
)

data class VerifyEmailCodeRequestDto(
    val email: String,
    val code: String,
)

data class EmailVerificationDataDto(
    val verified: Boolean,
)

data class AvailabilityDataDto(
    val available: Boolean,
)

data class TokenRequestDto(
    val refreshToken: String,
)

data class TokenRefreshDataDto(
    val accessToken: String,
)
