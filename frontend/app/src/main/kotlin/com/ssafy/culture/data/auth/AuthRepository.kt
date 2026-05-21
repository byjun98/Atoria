package com.ssafy.culture.data.auth

import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.domain.model.AuthSession
import com.ssafy.culture.domain.model.AuthUser
import com.ssafy.culture.domain.model.AvailabilityResult
import com.ssafy.culture.domain.model.EmailVerificationResult
import com.ssafy.culture.domain.model.SignupResult
import com.ssafy.culture.domain.model.TokenRefreshResult
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Retrofit

@Singleton
class AuthRepository @Inject constructor(
    retrofit: Retrofit,
    private val authTokenStore: AuthTokenStore,
) {
    private val authApi: AuthApi = retrofit.create(AuthApi::class.java)

    suspend fun getOAuthAuthorizationUrl(provider: String): String = withContext(Dispatchers.IO) {
        executeAuthCall {
            authApi.getOAuthAuthorization(provider.lowercase())
                .requireData()
                .authorizationUrl
        }
    }

    suspend fun login(
        email: String,
        password: String,
    ): AuthSession = withContext(Dispatchers.IO) {
        val loginId = email.trim()
        if (isSuperAccount(loginId, password)) {
            MockApiConfig.enabled = true
            val session = createSuperSession(loginId)
            authTokenStore.updateSession(session)
            authTokenStore.setDevAccessAllowed(true)
            return@withContext session
        }

        if (isMockApiEnabled()) {
            val session = createMockSession(loginId)
            authTokenStore.updateSession(session)
            authTokenStore.setDevAccessAllowed(false)
            return@withContext session
        }
        executeAuthCall {
            val session = authApi.login(
                LoginRequestDto(
                    email = loginId,
                    password = password,
                ),
            ).requireData().toDomain(loginId)
            authTokenStore.updateSession(session)
            authTokenStore.setDevAccessAllowed(false)
            session
        }
    }

    suspend fun signup(
        nickname: String,
        email: String,
        authCode: String,
        password: String,
        passwordConfirm: String,
    ): SignupResult = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            return@withContext SignupResult(
                userId = MOCK_USER_ID,
                email = email.trim(),
                nickname = nickname.trim(),
            )
        }
        executeAuthCall {
            val response = authApi.signup(
                SignupRequestDto(
                    nickname = nickname.trim(),
                    email = email.trim(),
                    authCode = authCode.trim(),
                    password = password,
                    passwordConfirm = passwordConfirm,
                ),
            )
            val body = response.body()
            if (!response.isSuccessful || body == null || !body.success) {
                throw AuthRepositoryException(
                    message = "회원가입 실패",
                    code = response.code(),
                    error = body?.error,
                )
            }
            body.requireData().toDomain()
        }
    }

    suspend fun sendEmailCode(email: String) = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            return@withContext
        }
        executeAuthCall {
            val response = authApi.sendEmailCode(EmailRequestDto(email = email.trim()))
            val body = response.body()
            if (!response.isSuccessful || response.code() != 200 || body == null || body.code != 200) {
                throw AuthRepositoryException(
                    message = response.code().toEmailCodeRequestFailureMessage(),
                    code = response.code(),
                    error = body?.error,
                )
            }
            body.requireSuccess()
        }
    }

    suspend fun verifyEmailCode(
        email: String,
        code: String,
    ): EmailVerificationResult = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            return@withContext EmailVerificationResult(verified = true)
        }
        executeAuthCall {
            val response = authApi.verifyEmailCode(
                VerifyEmailCodeRequestDto(
                    email = email.trim(),
                    code = code.trim(),
                ),
            )
            val body = response.body()
            if (!response.isSuccessful || body == null || !body.success) {
                throw AuthRepositoryException(
                    message = body?.message ?: "인증코드를 확인해 주세요.",
                    code = response.code(),
                    error = body?.error,
                )
            }
            body.requireData().toDomain()
        }
    }

    suspend fun checkEmailAvailability(email: String): AvailabilityResult = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            return@withContext AvailabilityResult(available = true)
        }
        try {
            executeAuthCall {
                authApi.checkEmailAvailability(email = email.trim()).requireData().toDomain()
            }
        } catch (exception: AuthRepositoryException) {
            // Backend currently throws 4xx (EMAIL_DUPLICATE -> 409) instead of returning available=false.
            // Map any duplicate-style error to a normal availability result so the UI can proceed.
            if (exception.code == 409 || exception.error?.contains("EMAIL_DUPLICATE", ignoreCase = true) == true) {
                AvailabilityResult(available = false)
            } else {
                throw exception
            }
        }
    }

    suspend fun checkNicknameAvailability(nickname: String): AvailabilityResult = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            return@withContext AvailabilityResult(available = true)
        }
        executeAuthCall {
            authApi.checkNicknameAvailability(nickname = nickname.trim()).requireData().toDomain()
        }
    }

    suspend fun logout(refreshToken: String) = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            authTokenStore.clear()
            return@withContext
        }
        executeAuthCall {
            authApi.logout(TokenRequestDto(refreshToken = refreshToken)).requireSuccess()
            authTokenStore.clear()
        }
    }

    suspend fun logoutCurrentSession() = withContext(Dispatchers.IO) {
        val refreshToken: String = authTokenStore.getRefreshToken()?.takeIf(String::isNotBlank)
            ?: run {
                authTokenStore.clear()
                return@withContext
            }
        if (!isMockApiEnabled()) {
            runCatching {
                authApi.logout(TokenRequestDto(refreshToken = refreshToken)).requireSuccess()
            }
        }
        authTokenStore.clear()
    }

    suspend fun refreshToken(refreshToken: String): TokenRefreshResult = withContext(Dispatchers.IO) {
        if (isMockApiEnabled()) {
            authTokenStore.updateAccessToken(MOCK_ACCESS_TOKEN)
            return@withContext TokenRefreshResult(accessToken = MOCK_ACCESS_TOKEN)
        }
        executeAuthCall {
            val result = authApi.refreshToken(TokenRequestDto(refreshToken = refreshToken)).requireData().toDomain()
            authTokenStore.updateAccessToken(result.accessToken)
            result
        }
    }

    private fun isMockApiEnabled(): Boolean =
        MockApiConfig.enabled
}

class AuthRepositoryException(
    override val message: String,
    val code: Int? = null,
    val error: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

private suspend fun <T> executeAuthCall(block: suspend () -> T): T =
    try {
        block()
    } catch (exception: HttpException) {
        throw exception.toAuthRepositoryException()
    } catch (exception: IOException) {
        throw AuthRepositoryException(
            message = "네트워크 연결을 확인해 주세요.",
            cause = exception,
        )
    }

private fun AuthApiResponseDto<*>.requireSuccess() {
    if (!success) {
        throw AuthRepositoryException(
            message = message ?: "요청에 실패했습니다.",
            code = code,
            error = error,
        )
    }
}

private fun <T : Any> AuthApiResponseDto<T>.requireData(): T {
    requireSuccess()
    return data ?: throw AuthRepositoryException(
        message = message ?: "응답 데이터가 없습니다.",
        code = code,
        error = error,
    )
}

private fun HttpException.toAuthRepositoryException(): AuthRepositoryException =
    AuthRepositoryException(
        message = when (code()) {
            400 -> "입력한 값을 확인해 주세요."
            401 -> "이메일 또는 비밀번호를 확인해 주세요."
            409 -> "이미 사용 중인 값입니다."
            else -> "서버 요청에 실패했습니다."
        },
        code = code(),
        cause = this,
    )

private fun Int.toEmailCodeRequestFailureMessage(): String =
    when (this) {
        400 -> "이메일 형식을 확인해 주세요."
        404 -> "이메일 인증 API를 찾지 못했어요. 서버 주소를 확인해 주세요. (404)"
        409 -> "이미 사용 중인 이메일입니다."
        in 500..599 -> "서버에서 이메일 인증 요청을 처리하지 못했어요. 백엔드 로그를 확인해 주세요. ($this)"
        else -> "이메일 인증 요청에 실패했어요. ($this)"
    }

private fun LoginDataDto.toDomain(email: String): AuthSession =
    AuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = AuthUser(
            userId = user.userId,
            nickname = user.nickname,
            email = email,
        ),
    )

private fun SignupDataDto.toDomain(): SignupResult =
    SignupResult(
        userId = userId,
        email = email,
        nickname = nickname,
    )

private fun AvailabilityDataDto.toDomain(): AvailabilityResult =
    AvailabilityResult(available = available)

private fun EmailVerificationDataDto.toDomain(): EmailVerificationResult =
    EmailVerificationResult(verified = verified)

private fun TokenRefreshDataDto.toDomain(): TokenRefreshResult =
    TokenRefreshResult(accessToken = accessToken)

private fun createMockSession(email: String): AuthSession =
    AuthSession(
        accessToken = MOCK_ACCESS_TOKEN,
        refreshToken = MOCK_REFRESH_TOKEN,
        user = AuthUser(
            userId = MOCK_USER_ID,
            nickname = MOCK_NICKNAME,
            email = email.trim(),
        ),
    )

private fun isSuperAccount(loginId: String, password: String): Boolean =
    loginId == SUPER_ACCOUNT_ID && password == SUPER_ACCOUNT_PASSWORD

private fun createSuperSession(loginId: String): AuthSession =
    AuthSession(
        accessToken = SUPER_ACCESS_TOKEN,
        refreshToken = SUPER_REFRESH_TOKEN,
        user = AuthUser(
            userId = SUPER_USER_ID,
            nickname = SUPER_NICKNAME,
            email = loginId,
        ),
    )

private const val SUPER_ACCESS_TOKEN = "super-dev-access-token"
private const val SUPER_REFRESH_TOKEN = "super-dev-refresh-token"
private const val SUPER_USER_ID = -12L
private const val SUPER_NICKNAME = "Dev Super"
private const val SUPER_ACCOUNT_ID = "ssafy12"
private const val SUPER_ACCOUNT_PASSWORD = "qwerkkm123!"

private const val MOCK_ACCESS_TOKEN = "mock-access-token"
private const val MOCK_REFRESH_TOKEN = "mock-refresh-token"
private const val MOCK_USER_ID = 1L
private const val MOCK_NICKNAME = "문화 탐험가"
