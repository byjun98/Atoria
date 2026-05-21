package com.ssafy.culture.data.user

import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.data.auth.AuthTokenStore
import com.ssafy.culture.domain.model.PasswordChange
import com.ssafy.culture.domain.model.UserProfile
import com.ssafy.culture.domain.model.UserProfileUpdate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

@Singleton
class UserRepository @Inject constructor(
    retrofit: Retrofit,
    private val authTokenStore: AuthTokenStore,
) {
    private val userApi: UserApi = retrofit.create(UserApi::class.java)
    private val currentUser = MutableStateFlow(MockUserProfile)

    fun observeCurrentUser(): Flow<UserProfile> = currentUser.asStateFlow()

    suspend fun refreshCurrentUser(): UserProfile = withContext(Dispatchers.IO) {
        val user = if (MockApiConfig.enabled) {
            currentUser.value
        } else {
            userApi.getMyPage().requireData().toDomain()
        }
        currentUser.value = user
        user
    }

    suspend fun updateProfile(update: UserProfileUpdate): UserProfile = withContext(Dispatchers.IO) {
        val user = if (MockApiConfig.enabled) {
            currentUser.value.copy(
                nickname = update.nickname,
                email = update.email,
            )
        } else {
            userApi.updateMyPage(
                UpdateUserProfileRequest(
                    nickname = update.nickname,
                ),
            )
            refreshCurrentUser()
        }
        currentUser.value = user
        authTokenStore.updateNickname(user.nickname)
        user
    }

    suspend fun changePassword(change: PasswordChange): Unit = withContext(Dispatchers.IO) {
        if (!MockApiConfig.enabled) {
            userApi.changePassword(
                ChangePasswordRequest(
                    currentPassword = change.currentPassword,
                    newPassword = change.newPassword,
                    newPasswordConfirm = change.newPassword,
                ),
            )
        }
    }

    suspend fun deleteAccount(password: String? = null): Unit = withContext(Dispatchers.IO) {
        if (!MockApiConfig.enabled) {
            userApi.deleteAccount(DeleteAccountRequest(password = password)).requireSuccess()
        }
        authTokenStore.clear()
        currentUser.value = MockUserProfile
    }
}

private fun <T> com.ssafy.culture.data.remote.ApiResponse<T>.requireData(): T =
    data ?: error(message)

private fun com.ssafy.culture.data.remote.ApiResponse<*>.requireSuccess() {
    check(success) { message }
}

private val MockUserProfile = UserProfile(
    id = 1L,
    email = "culture@example.com",
    nickname = "문화 탐험가",
    profileImageUrl = null,
    provider = "local",
    joinedAt = "2026-04-01",
    completedCourseCount = 3,
    badgeCount = 3,
)
