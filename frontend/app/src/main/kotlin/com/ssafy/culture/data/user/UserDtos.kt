package com.ssafy.culture.data.user

import com.google.gson.annotations.SerializedName
import com.ssafy.culture.domain.model.UserProfile

data class UserProfileDto(
    @SerializedName("userId")
    val userId: Long? = null,
    val id: Long? = null,
    val email: String? = null,
    val nickname: String? = null,
    @SerializedName("profileImageUrl")
    val profileImageUrl: String? = null,
    val provider: String? = null,
    val joinedAt: String? = null,
    val completedCourseCount: Int? = null,
    val badgeCount: Int? = null,
)

data class UpdateUserProfileRequest(
    val nickname: String,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val newPasswordConfirm: String,
)

data class DeleteAccountRequest(
    val password: String? = null,
)

internal fun UserProfileDto.toDomain(): UserProfile =
    UserProfile(
        id = userId ?: id ?: 0L,
        email = email.orEmpty(),
        nickname = nickname.orEmpty(),
        profileImageUrl = profileImageUrl,
        provider = provider.orEmpty(),
        joinedAt = joinedAt,
        completedCourseCount = completedCourseCount ?: 0,
        badgeCount = badgeCount ?: 0,
    )

internal fun UserProfile.toDto(): UserProfileDto =
    UserProfileDto(
        id = id,
        email = email,
        nickname = nickname,
        profileImageUrl = profileImageUrl,
        provider = provider,
        joinedAt = joinedAt,
        completedCourseCount = completedCourseCount,
        badgeCount = badgeCount,
    )
