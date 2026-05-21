package com.ssafy.culture.domain.model

data class AuthUser(
    val userId: Long,
    val nickname: String,
    val email: String? = null,
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: AuthUser,
)

data class SignupResult(
    val userId: Long,
    val email: String,
    val nickname: String,
)

data class AvailabilityResult(
    val available: Boolean,
)

data class EmailVerificationResult(
    val verified: Boolean,
)

data class TokenRefreshResult(
    val accessToken: String,
)
