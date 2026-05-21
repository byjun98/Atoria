package com.ssafy.culture.data.auth

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.domain.model.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

@Singleton
class AuthTokenStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var accessToken: String? = preferences.getString(KEY_ACCESS_TOKEN, null)

    @Volatile
    private var refreshToken: String? = preferences.getString(KEY_REFRESH_TOKEN, null)

    @Volatile
    private var nickname: String? = preferences.getString(KEY_NICKNAME, null)

    @Volatile
    private var autoLoginEnabled: Boolean =
        preferences.getBoolean(KEY_AUTO_LOGIN_ENABLED, DEFAULT_AUTO_LOGIN_ENABLED)

    @Volatile
    private var savedLoginEmail: String? = preferences.getString(KEY_SAVED_LOGIN_EMAIL, null)

    @Volatile
    private var saveLoginEmailEnabled: Boolean =
        preferences.getBoolean(KEY_SAVE_LOGIN_EMAIL_ENABLED, DEFAULT_SAVE_LOGIN_EMAIL_ENABLED)

    @Volatile
    private var devAccessAllowed: Boolean = preferences.getBoolean(KEY_DEV_ACCESS_ALLOWED, false)

    private val _devAccessAllowed = MutableStateFlow(devAccessAllowed)
    val devAccessAllowedFlow: StateFlow<Boolean> = _devAccessAllowed.asStateFlow()

    fun updateSession(session: AuthSession) {
        accessToken = session.accessToken
        refreshToken = session.refreshToken
        nickname = session.user.nickname
        preferences.edit {
            putString(KEY_ACCESS_TOKEN, session.accessToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putString(KEY_NICKNAME, session.user.nickname)
        }
    }

    fun updateAccessToken(token: String) {
        accessToken = token
        preferences.edit {
            putString(KEY_ACCESS_TOKEN, token)
        }
    }

    fun updateNickname(value: String) {
        nickname = value
        preferences.edit {
            putString(KEY_NICKNAME, value)
        }
    }

    fun setAutoLoginEnabled(isEnabled: Boolean) {
        autoLoginEnabled = isEnabled
        preferences.edit {
            putBoolean(KEY_AUTO_LOGIN_ENABLED, isEnabled)
        }
    }

    fun setSaveLoginEmailEnabled(isEnabled: Boolean) {
        saveLoginEmailEnabled = isEnabled
        preferences.edit {
            putBoolean(KEY_SAVE_LOGIN_EMAIL_ENABLED, isEnabled)
            if (!isEnabled) {
                remove(KEY_SAVED_LOGIN_EMAIL)
            }
        }
        if (!isEnabled) {
            savedLoginEmail = null
        }
    }

    fun updateSavedLoginEmail(email: String) {
        val trimmedEmail = email.trim()
        savedLoginEmail = trimmedEmail.takeIf(String::isNotBlank)
        preferences.edit {
            if (trimmedEmail.isBlank()) {
                remove(KEY_SAVED_LOGIN_EMAIL)
            } else {
                putString(KEY_SAVED_LOGIN_EMAIL, trimmedEmail)
            }
        }
    }

    fun setDevAccessAllowed(isAllowed: Boolean) {
        devAccessAllowed = isAllowed
        _devAccessAllowed.value = isAllowed
        preferences.edit {
            putBoolean(KEY_DEV_ACCESS_ALLOWED, isAllowed)
        }
    }

    fun clear() {
        accessToken = null
        refreshToken = null
        nickname = null
        setDevAccessAllowed(false)
        preferences.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_NICKNAME)
        }
    }

    fun getAccessToken(): String? = accessToken

    fun getRefreshToken(): String? = refreshToken

    fun getNickname(): String? = nickname?.takeIf(String::isNotBlank)

    fun isAutoLoginEnabled(): Boolean = autoLoginEnabled

    fun isSaveLoginEmailEnabled(): Boolean = saveLoginEmailEnabled

    fun isDevAccessAllowed(): Boolean = devAccessAllowed

    fun getSavedLoginEmail(): String? = savedLoginEmail?.takeIf(String::isNotBlank)

    fun getAuthorizationHeader(): String? =
        accessToken?.takeIf(String::isNotBlank)?.let { token -> "Bearer $token" }

    private companion object {
        const val PREFERENCES_NAME = "auth_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_NICKNAME = "nickname"
        const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        const val KEY_SAVE_LOGIN_EMAIL_ENABLED = "save_login_email_enabled"
        const val KEY_SAVED_LOGIN_EMAIL = "saved_login_email"
        const val KEY_DEV_ACCESS_ALLOWED = "dev_access_allowed"
        const val DEFAULT_AUTO_LOGIN_ENABLED = true
        const val DEFAULT_SAVE_LOGIN_EMAIL_ENABLED = false
    }
}

@Singleton
class AuthHeaderInterceptor @Inject constructor(
    private val authTokenStore: AuthTokenStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath.trimStart('/')
        val authorizationHeader = authTokenStore.getAuthorizationHeader()
        val shouldSkip = path.contains("auth/") || path.contains("oauth2/")
        val authorizedRequest = if (
            authorizationHeader != null &&
            request.header("Authorization") == null &&
            !shouldSkip
        ) {
            request.newBuilder()
                .addHeader("Authorization", authorizationHeader)
                .build()
        } else {
            request
        }
        return chain.proceed(authorizedRequest)
    }
}

@Singleton
class AuthTokenRefreshAuthenticator @Inject constructor(
    private val authTokenStore: AuthTokenStore,
    private val gson: Gson,
) : Authenticator {
    private val refreshClient = OkHttpClient()
    private val refreshLock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val originalRequest = response.request
        val path = originalRequest.url.encodedPath.trimStart('/')
        if (path.contains("auth/") || response.responseCount >= MAX_AUTH_RETRIES) {
            return null
        }

        if (authTokenStore.isDevAccessAllowed()) {
            return null
        }

        if (authTokenStore.getRefreshToken()?.takeIf(String::isNotBlank) == null) return null
        val requestAccessToken: String? = originalRequest.bearerToken()
        return synchronized(refreshLock) {
            val currentAccessToken: String? = authTokenStore.getAccessToken()?.takeIf(String::isNotBlank)
            if (currentAccessToken != null && currentAccessToken != requestAccessToken) {
                return@synchronized originalRequest.withBearerToken(currentAccessToken)
            }
            val latestRefreshToken: String = authTokenStore.getRefreshToken()?.takeIf(String::isNotBlank)
                ?: return@synchronized null
            val newAccessToken: String = refreshAccessToken(latestRefreshToken)
                ?: run {
                    authTokenStore.clear()
                    return@synchronized null
                }
            authTokenStore.updateAccessToken(newAccessToken)
            originalRequest.withBearerToken(newAccessToken)
        }
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        val requestBody = gson.toJson(TokenRequestDto(refreshToken = refreshToken))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL + "auth/token/refresh")
            .post(requestBody)
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val responseBody = response.body.string()
                gson.fromJson(responseBody, RefreshTokenResponseDto::class.java)
                    ?.takeIf { it.success }
                    ?.data
                    ?.accessToken
            }
        }.getOrNull()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_AUTH_RETRIES = 2
    }
}

private fun Request.bearerToken(): String? =
    header("Authorization")
        ?.removePrefix("Bearer")
        ?.trim()
        ?.takeIf(String::isNotBlank)

private fun Request.withBearerToken(token: String): Request =
    newBuilder()
        .header("Authorization", "Bearer $token")
        .build()

private val Response.responseCount: Int
    get() {
        var result = 1
        var prior = this.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

private data class RefreshTokenResponseDto(
    val success: Boolean,
    val code: Int,
    val message: String? = null,
    val data: TokenRefreshDataDto? = null,
    val error: String? = null,
)
