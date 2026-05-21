package com.ssafy.culture.domain.model

data class UserProfile(
    val id: Long,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val provider: String,
    val joinedAt: String?,
    val completedCourseCount: Int,
    val badgeCount: Int,
)

data class UserProfileUpdate(
    val nickname: String,
    val email: String,
)

data class PasswordChange(
    val currentPassword: String,
    val newPassword: String,
)
