package com.ssafy.culture.ui.screen.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.auth.AuthRepository
import com.ssafy.culture.data.auth.AuthRepositoryException
import com.ssafy.culture.ui.component.ImmersiveNavigationBarEffect
import com.ssafy.culture.ui.layout.CultureHeightClass
import com.ssafy.culture.ui.layout.cultureHeightClass
import com.ssafy.culture.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val EMAIL_CODE_TTL_SECONDS = 300L

private data class SignupLayoutMetrics(
    val screenTopPadding: Dp,
    val screenBottomPadding: Dp,
    val horizontalPadding: Dp,
    val topBarBottomSpacing: Dp,
    val cardHorizontalPadding: Dp,
    val cardVerticalPadding: Dp,
    val cardSpacing: Dp,
    val showMascot: Boolean,
    val mascotSize: Dp,
    val mascotPrimaryIconSize: Dp,
    val mascotSecondaryIconSize: Dp,
    val fieldMinHeight: Dp,
    val fieldVerticalPadding: Dp,
    val submitButtonHeight: Dp,
)

sealed interface EmailAuthStage {
    data object Idle : EmailAuthStage
    data object Sending : EmailAuthStage
    data class AwaitingCode(val remainingSeconds: Long) : EmailAuthStage
    data class Verifying(val remainingSeconds: Long) : EmailAuthStage
    data object Verified : EmailAuthStage
    data object Expired : EmailAuthStage
}

data class SignupUiState(
    val nickname: String = "",
    val email: String = "",
    val authCode: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val emailAuthStage: EmailAuthStage = EmailAuthStage.Idle,
    val isEmailChecking: Boolean = false,
    val isEmailAvailable: Boolean? = null,
    val emailCheckMessage: String? = null,
    val isSignupComplete: Boolean = false,
) {
    val isEmailVerified: Boolean
        get() = emailAuthStage is EmailAuthStage.Verified

    val isEmailCodeStageActive: Boolean
        get() = emailAuthStage is EmailAuthStage.AwaitingCode ||
            emailAuthStage is EmailAuthStage.Verifying ||
            emailAuthStage is EmailAuthStage.Expired ||
            emailAuthStage is EmailAuthStage.Verified

    val passwordsMatch: Boolean
        get() = password.isNotBlank() && password == passwordConfirm

    val passwordMeetsRequirement: Boolean
        get() = password.isValidPasswordFormat()

    val passwordMessage: String?
        get() = when {
            password.isBlank() || passwordConfirm.isBlank() -> null
            passwordsMatch -> "비밀번호가 일치합니다."
            else -> "비밀번호가 일치하지 않습니다."
        }
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SignupUiState())
    val uiState: StateFlow<SignupUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun onNicknameChange(nickname: String) {
        _uiState.update { state ->
            state.copy(nickname = nickname, errorMessage = null)
        }
    }

    fun onEmailChange(email: String) {
        if (email == _uiState.value.email) return
        countdownJob?.cancel()
        _uiState.update { state ->
            state.copy(
                email = email,
                errorMessage = null,
                successMessage = null,
                emailAuthStage = EmailAuthStage.Idle,
                authCode = "",
                isEmailChecking = false,
                isEmailAvailable = null,
                emailCheckMessage = null,
            )
        }
    }

    fun onAuthCodeChange(authCode: String) {
        _uiState.update { state ->
            state.copy(
                authCode = authCode,
                errorMessage = null,
                successMessage = null,
            )
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { state ->
            state.copy(password = password, errorMessage = null)
        }
    }

    fun onPasswordConfirmChange(passwordConfirm: String) {
        _uiState.update { state ->
            state.copy(passwordConfirm = passwordConfirm, errorMessage = null)
        }
    }

    fun requestEmailCode() {
        val state = _uiState.value
        if (state.isLoading || state.emailAuthStage is EmailAuthStage.Sending) return
        val email = state.email.trim()
        if (email.isBlank()) {
            showError("이메일을 입력해 주세요.")
            return
        }
        if (!email.isValidEmailFormat()) {
            showError("이메일 형식을 다시 확인해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    emailAuthStage = EmailAuthStage.Sending,
                    isEmailChecking = true,
                    errorMessage = null,
                    successMessage = null,
                )
            }
            val availability = runCatching {
                authRepository.checkEmailAvailability(email)
            }
            availability.onFailure { throwable ->
                val message = (throwable as? AuthRepositoryException)?.message
                    ?: "이메일 확인 중 문제가 발생했습니다."
                _uiState.update { current ->
                    current.copy(
                        emailAuthStage = EmailAuthStage.Idle,
                        isEmailChecking = false,
                        isEmailAvailable = null,
                        emailCheckMessage = null,
                        errorMessage = message,
                    )
                }
                return@launch
            }
            val available = availability.getOrNull()?.available ?: true
            if (!available) {
                _uiState.update { current ->
                    current.copy(
                        emailAuthStage = EmailAuthStage.Idle,
                        isEmailChecking = false,
                        isEmailAvailable = false,
                        emailCheckMessage = "이미 사용 중인 이메일입니다.",
                        errorMessage = null,
                    )
                }
                return@launch
            }

            _uiState.update { current ->
                current.copy(
                    isEmailChecking = false,
                    isEmailAvailable = true,
                    emailCheckMessage = "사용할 수 있는 이메일이에요.",
                )
            }

            runCatching {
                authRepository.sendEmailCode(email)
            }.onSuccess {
                startEmailCodeCountdown()
                _uiState.update { current ->
                    current.copy(
                        successMessage = "인증 코드를 보냈어요. 메일을 확인해 주세요.",
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        emailAuthStage = EmailAuthStage.Idle,
                        errorMessage = throwable.message ?: "인증 코드 발송에 실패했습니다.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun verifyEmailCode() {
        val state = _uiState.value
        if (state.isLoading) return
        val stage = state.emailAuthStage
        val remaining = when (stage) {
            is EmailAuthStage.AwaitingCode -> stage.remainingSeconds
            is EmailAuthStage.Verifying -> stage.remainingSeconds
            else -> {
                showError("인증 코드를 먼저 요청해 주세요.")
                return
            }
        }
        if (state.email.isBlank() || state.authCode.isBlank()) {
            showError("이메일과 인증코드를 입력해 주세요.")
            return
        }
        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    emailAuthStage = EmailAuthStage.Verifying(remaining),
                    errorMessage = null,
                    successMessage = null,
                )
            }
            runCatching {
                authRepository.verifyEmailCode(
                    email = state.email,
                    code = state.authCode,
                )
            }.onSuccess { result ->
                if (result.verified) {
                    countdownJob?.cancel()
                    _uiState.update { current ->
                        current.copy(
                            emailAuthStage = EmailAuthStage.Verified,
                            successMessage = "이메일 인증이 완료되었습니다.",
                            errorMessage = null,
                        )
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            emailAuthStage = EmailAuthStage.AwaitingCode(remaining),
                            errorMessage = "인증코드를 확인해 주세요.",
                            successMessage = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update { current ->
                    current.copy(
                        emailAuthStage = EmailAuthStage.AwaitingCode(remaining),
                        errorMessage = throwable.message ?: "인증코드를 확인해 주세요.",
                        successMessage = null,
                    )
                }
            }
        }
    }

    fun signup() {
        val state = _uiState.value
        if (state.isLoading) return
        val validationMessage = state.getValidationMessage()
        if (validationMessage != null) {
            showError(validationMessage)
            return
        }
        viewModelScope.launch {
            startLoading()
            runCatching {
                authRepository.signup(
                    nickname = state.nickname,
                    email = state.email,
                    authCode = state.authCode,
                    password = state.password,
                    passwordConfirm = state.passwordConfirm,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        successMessage = "회원가입 완료",
                        errorMessage = null,
                        isSignupComplete = true,
                    )
                }
            }.onFailure {
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        errorMessage = "회원가입 실패",
                        successMessage = null,
                        isSignupComplete = false,
                    )
                }
            }
        }
    }

    fun consumeSignupComplete() {
        _uiState.update { state ->
            state.copy(isSignupComplete = false)
        }
    }

    private fun startEmailCodeCountdown() {
        countdownJob?.cancel()
        _uiState.update { current ->
            current.copy(emailAuthStage = EmailAuthStage.AwaitingCode(EMAIL_CODE_TTL_SECONDS))
        }
        countdownJob = viewModelScope.launch {
            var remaining = EMAIL_CODE_TTL_SECONDS
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                val current = _uiState.value
                when (val stage = current.emailAuthStage) {
                    is EmailAuthStage.AwaitingCode ->
                        _uiState.update { it.copy(emailAuthStage = EmailAuthStage.AwaitingCode(remaining)) }
                    is EmailAuthStage.Verifying ->
                        _uiState.update { it.copy(emailAuthStage = EmailAuthStage.Verifying(remaining)) }
                    EmailAuthStage.Verified, EmailAuthStage.Expired,
                    EmailAuthStage.Idle, EmailAuthStage.Sending -> return@launch
                }
            }
            _uiState.update { current ->
                if (current.emailAuthStage is EmailAuthStage.Verified) current
                else current.copy(emailAuthStage = EmailAuthStage.Expired)
            }
        }
    }

    private fun startLoading() {
        _uiState.update { state ->
            state.copy(isLoading = true, errorMessage = null, successMessage = null)
        }
    }

    private fun showError(message: String) {
        _uiState.update { state ->
            state.copy(errorMessage = message, successMessage = null)
        }
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }
}

private fun SignupUiState.getValidationMessage(): String? =
    when {
        password.isNotBlank() && !passwordMeetsRequirement -> "비밀번호는 영문, 숫자, 특수문자를 포함해 8자 이상이어야 합니다."
        nickname.isBlank() -> "닉네임을 입력해 주세요."
        email.isBlank() -> "이메일을 입력해 주세요."
        authCode.isBlank() -> "인증코드를 입력해 주세요."
        !isEmailVerified -> "이메일 인증을 완료해 주세요."
        password.isBlank() -> "비밀번호를 입력해 주세요."
        passwordConfirm.isBlank() -> "비밀번호 확인을 입력해 주세요."
        password != passwordConfirm -> "비밀번호가 일치하지 않습니다."
        else -> null
    }

private fun String.isValidEmailFormat(): Boolean =
    EMAIL_PATTERN.matches(trim())

private fun String.isValidPasswordFormat(): Boolean =
    PASSWORD_PATTERN.matches(this)

private val EMAIL_PATTERN = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val PASSWORD_PATTERN = Regex("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$")

@Composable
fun SignupRoute(
    onBack: () -> Unit,
    onSignupComplete: () -> Unit,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSignupComplete) {
        if (uiState.isSignupComplete) {
            delay(800)
            viewModel.consumeSignupComplete()
            onSignupComplete()
        }
    }

    SignupScreen(
        uiState = uiState,
        onNicknameChange = viewModel::onNicknameChange,
        onEmailChange = viewModel::onEmailChange,
        onAuthCodeChange = viewModel::onAuthCodeChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordConfirmChange = viewModel::onPasswordConfirmChange,
        onRequestEmailCode = viewModel::requestEmailCode,
        onVerifyEmailCode = viewModel::verifyEmailCode,
        onSignupClick = viewModel::signup,
        onBack = onBack,
    )
}

@Composable
private fun SignupScreen(
    uiState: SignupUiState,
    onNicknameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onRequestEmailCode: () -> Unit,
    onVerifyEmailCode: () -> Unit,
    onSignupClick: () -> Unit,
    onBack: () -> Unit,
) {
    ImmersiveNavigationBarEffect()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SignupBackground)
            .imePadding(),
    ) {
        val metrics = signupLayoutMetrics(
            maxHeight = maxHeight,
            emailAuthStage = uiState.emailAuthStage,
        )

        SignupGlow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = metrics.horizontalPadding)
                .padding(top = metrics.screenTopPadding, bottom = metrics.screenBottomPadding),
        ) {
            SignupTopBar(onBack = onBack)
            Spacer(modifier = Modifier.height(metrics.topBarBottomSpacing))
            SignupCard(
                uiState = uiState,
                onNicknameChange = onNicknameChange,
                onEmailChange = onEmailChange,
                onAuthCodeChange = onAuthCodeChange,
                onPasswordChange = onPasswordChange,
                onPasswordConfirmChange = onPasswordConfirmChange,
                onRequestEmailCode = onRequestEmailCode,
                onVerifyEmailCode = onVerifyEmailCode,
                onSignupClick = onSignupClick,
                metrics = metrics,
            )
        }
    }
}

private fun signupLayoutMetrics(
    maxHeight: Dp,
    emailAuthStage: EmailAuthStage,
): SignupLayoutMetrics =
    when (cultureHeightClass(maxHeight)) {
        CultureHeightClass.Compact -> SignupLayoutMetrics(
            screenTopPadding = 12.dp,
            screenBottomPadding = 20.dp,
            horizontalPadding = 18.dp,
            topBarBottomSpacing = 12.dp,
            cardHorizontalPadding = 18.dp,
            cardVerticalPadding = 18.dp,
            cardSpacing = 8.dp,
            showMascot = emailAuthStage is EmailAuthStage.Idle,
            mascotSize = 72.dp,
            mascotPrimaryIconSize = 36.dp,
            mascotSecondaryIconSize = 20.dp,
            fieldMinHeight = 52.dp,
            fieldVerticalPadding = 5.dp,
            submitButtonHeight = 52.dp,
        )
        CultureHeightClass.Medium -> SignupLayoutMetrics(
            screenTopPadding = 18.dp,
            screenBottomPadding = 28.dp,
            horizontalPadding = 20.dp,
            topBarBottomSpacing = 18.dp,
            cardHorizontalPadding = 20.dp,
            cardVerticalPadding = 22.dp,
            cardSpacing = 10.dp,
            showMascot = true,
            mascotSize = 86.dp,
            mascotPrimaryIconSize = 42.dp,
            mascotSecondaryIconSize = 24.dp,
            fieldMinHeight = 54.dp,
            fieldVerticalPadding = 6.dp,
            submitButtonHeight = 54.dp,
        )
        CultureHeightClass.Expanded -> SignupLayoutMetrics(
            screenTopPadding = 22.dp,
            screenBottomPadding = 34.dp,
            horizontalPadding = 22.dp,
            topBarBottomSpacing = 22.dp,
            cardHorizontalPadding = 22.dp,
            cardVerticalPadding = 24.dp,
            cardSpacing = Spacing.Sm,
            showMascot = true,
            mascotSize = 96.dp,
            mascotPrimaryIconSize = 46.dp,
            mascotSecondaryIconSize = 26.dp,
            fieldMinHeight = 56.dp,
            fieldVerticalPadding = 6.dp,
            submitButtonHeight = 56.dp,
        )
    }

@Composable
private fun SignupGlow() {
    Box(
        modifier = Modifier
            .size(430.dp)
            .offset(y = 92.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFF6C8).copy(alpha = 0.92f),
                        Color(0xFFFFE9F0).copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                ),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun SignupTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color(0xFF492136),
            )
        }
        Text(
            text = "회원가입",
            modifier = Modifier.weight(1f),
            color = Color(0xFF492136),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
            textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.size(48.dp))
    }
}

@Composable
private fun SignupCard(
    uiState: SignupUiState,
    onNicknameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onRequestEmailCode: () -> Unit,
    onVerifyEmailCode: () -> Unit,
    onSignupClick: () -> Unit,
    metrics: SignupLayoutMetrics,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Color.White,
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = metrics.cardHorizontalPadding,
                vertical = metrics.cardVerticalPadding,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(metrics.cardSpacing),
        ) {
            if (metrics.showMascot) {
                SignupMascotBadge(metrics = metrics)
            }
            SignupInputField(
                label = "닉네임",
                value = uiState.nickname,
                onValueChange = onNicknameChange,
                metrics = metrics,
            )
            EmailAuthSection(
                uiState = uiState,
                onEmailChange = onEmailChange,
                onAuthCodeChange = onAuthCodeChange,
                onRequestEmailCode = onRequestEmailCode,
                onVerifyEmailCode = onVerifyEmailCode,
                metrics = metrics,
            )
            SignupInputField(
                label = "비밀번호",
                value = uiState.password,
                onValueChange = onPasswordChange,
                isPassword = true,
                metrics = metrics,
            )
            Text(
                text = "영문, 숫자, 특수문자를 포함해 8자 이상",
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    uiState.password.isBlank() -> Color(0xFF9A7586)
                    uiState.passwordMeetsRequirement -> Color(0xFF2E9D55)
                    else -> Color(0xFFE03B50)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
            SignupInputField(
                label = "비밀번호 확인",
                value = uiState.passwordConfirm,
                onValueChange = onPasswordConfirmChange,
                isPassword = true,
                metrics = metrics,
            )
            val passwordMessage = uiState.passwordMessage
            if (passwordMessage != null) {
                Text(
                    text = passwordMessage,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (uiState.passwordsMatch) {
                        Color(0xFF2E9D55)
                    } else {
                        Color(0xFFE03B50)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                )
            }
            AuthStateMessage(
                errorMessage = uiState.errorMessage,
                successMessage = uiState.successMessage,
            )
            SignupSubmitButton(
                onSignupClick = onSignupClick,
                uiState = uiState,
                height = metrics.submitButtonHeight,
            )
        }
    }
}

@Composable
private fun EmailAuthSection(
    uiState: SignupUiState,
    onEmailChange: (String) -> Unit,
    onAuthCodeChange: (String) -> Unit,
    onRequestEmailCode: () -> Unit,
    onVerifyEmailCode: () -> Unit,
    metrics: SignupLayoutMetrics,
) {
    val stage = uiState.emailAuthStage
    val emailReadOnly = uiState.isEmailCodeStageActive
    val requestLabel = when {
        stage is EmailAuthStage.Sending || uiState.isEmailChecking -> "확인 중"
        stage is EmailAuthStage.AwaitingCode || stage is EmailAuthStage.Verifying -> "재전송"
        stage is EmailAuthStage.Expired -> "재전송"
        stage is EmailAuthStage.Verified -> "완료"
        else -> "인증 요청"
    }
    val requestEnabled = !uiState.isLoading &&
        stage !is EmailAuthStage.Sending &&
        stage !is EmailAuthStage.Verified &&
        !uiState.isEmailChecking

    SignupInputField(
        label = "이메일",
        value = uiState.email,
        onValueChange = onEmailChange,
        keyboardType = KeyboardType.Email,
        readOnly = emailReadOnly && stage !is EmailAuthStage.Expired,
        metrics = metrics,
        trailing = {
            SignupTrailingChip(
                label = requestLabel,
                enabled = requestEnabled,
                onClick = onRequestEmailCode,
                highlight = stage is EmailAuthStage.Verified,
            )
        },
    )

    val checkMessage = uiState.emailCheckMessage
    if (checkMessage != null && stage !is EmailAuthStage.Verified) {
        Text(
            text = checkMessage,
            modifier = Modifier.fillMaxWidth(),
            color = if (uiState.isEmailAvailable == true) Color(0xFF2E9D55) else Color(0xFFE03B50),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
        )
    }

    when (stage) {
        EmailAuthStage.Idle, EmailAuthStage.Sending -> Unit

        is EmailAuthStage.AwaitingCode,
        is EmailAuthStage.Verifying,
        EmailAuthStage.Expired -> {
            val remaining = when (stage) {
                is EmailAuthStage.AwaitingCode -> stage.remainingSeconds
                is EmailAuthStage.Verifying -> stage.remainingSeconds
                else -> 0L
            }
            val isVerifying = stage is EmailAuthStage.Verifying
            val isExpired = stage is EmailAuthStage.Expired

            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(stage::class) {
                if (!isExpired) {
                    runCatching { focusRequester.requestFocus() }
                }
            }

            SignupInputField(
                label = "인증코드 (메일에 도착한 6자리)",
                value = uiState.authCode,
                onValueChange = onAuthCodeChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.focusRequester(focusRequester),
                metrics = metrics,
                trailing = {
                    if (isExpired) {
                        Text(
                            text = "만료됨",
                            color = Color(0xFFE03B50),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    } else {
                        SignupTrailingChip(
                            label = if (isVerifying) "확인 중" else "확인",
                            enabled = !uiState.isLoading && !isVerifying && uiState.authCode.isNotBlank(),
                            onClick = onVerifyEmailCode,
                        )
                    }
                },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (isExpired) {
                    Text(
                        text = "인증 시간이 지났어요. 다시 요청해 주세요.",
                        color = Color(0xFFE03B50),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF8D3C56),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = formatRemaining(remaining),
                            color = Color(0xFF6C2C3D),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                        )
                    }
                    Text(
                        text = "메일이 안 왔다면 재전송",
                        color = Color(0xFF8D3C56),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }

        EmailAuthStage.Verified -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFEAF7EE),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF2E9D55),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "이메일 인증이 완료됐어요.",
                        color = Color(0xFF1F6B3D),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    )
                }
            }
        }
    }
}

private fun formatRemaining(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val secs = safe % 60
    return "%02d:%02d".format(minutes, secs)
}

@Composable
private fun SignupMascotBadge(metrics: SignupLayoutMetrics) {
    Box(
        modifier = Modifier
            .size(metrics.mascotSize)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF76A0), Color(0xFFFFC5D7))))
            .border(4.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFE27A),
            modifier = Modifier.size(metrics.mascotPrimaryIconSize),
        )
        Icon(
            imageVector = Icons.Rounded.MarkEmailRead,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(metrics.mascotSecondaryIconSize),
        )
    }
}

@Composable
private fun SignupInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
    metrics: SignupLayoutMetrics,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = metrics.fieldMinHeight)
            .wrapContentHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(if (readOnly) Color(0xFFFAF1F4) else Color(0xFFFFF7FA))
            .border(1.dp, Color(0xFFFFBDD2), RoundedCornerShape(20.dp))
            .padding(
                start = 18.dp,
                end = 10.dp,
                top = metrics.fieldVerticalPadding,
                bottom = metrics.fieldVerticalPadding,
            )
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color(0xFF492136),
                fontWeight = FontWeight.Medium,
            ),
            singleLine = true,
            readOnly = readOnly,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(
                        text = label,
                        color = Color(0xFF9A7586),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                innerTextField()
            },
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SignupTrailingChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val background = when {
        highlight -> Color(0xFFB8E8C5)
        enabled -> Color(0xFFFFD45F)
        else -> Color(0xFFEDE0E5)
    }
    val textColor = when {
        highlight -> Color(0xFF1F6B3D)
        enabled -> Color(0xFF6C2C3D)
        else -> Color(0xFF9A7586)
    }
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = background,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xxs),
        ) {
            if (highlight) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = textColor,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
            )
        }
    }
}

@Composable
private fun AuthStateMessage(
    errorMessage: String?,
    successMessage: String?,
) {
    val message = errorMessage ?: successMessage ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (errorMessage != null) Icons.Rounded.Cancel else Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (errorMessage != null) MaterialTheme.colorScheme.error else Color(0xFF2F7D4F),
            modifier = Modifier
                .padding(end = Spacing.Xxs)
                .size(14.dp),
        )
        Text(
            text = message,
            color = if (errorMessage != null) {
                MaterialTheme.colorScheme.error
            } else {
                Color(0xFF2F7D4F)
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun SignupSubmitButton(
    onSignupClick: () -> Unit,
    uiState: SignupUiState,
    height: Dp,
) {
    val stage = uiState.emailAuthStage
    val allFieldsFilled = uiState.nickname.isNotBlank() &&
        uiState.email.isNotBlank() &&
        uiState.authCode.isNotBlank() &&
        uiState.password.isNotBlank() &&
        uiState.passwordConfirm.isNotBlank()
    val readyToSubmit = stage is EmailAuthStage.Verified &&
        allFieldsFilled &&
        uiState.passwordsMatch &&
        uiState.passwordMeetsRequirement
    val buttonEnabled = readyToSubmit && !uiState.isLoading

    val label = when {
        uiState.isLoading -> "가입 처리 중"
        stage is EmailAuthStage.Sending || uiState.isEmailChecking -> "이메일 확인 중"
        stage is EmailAuthStage.Verifying -> "인증 확인 중"
        stage is EmailAuthStage.AwaitingCode -> "이메일 인증을 완료해 주세요"
        stage is EmailAuthStage.Expired -> "인증 코드를 다시 요청해 주세요"
        stage !is EmailAuthStage.Verified -> "이메일 인증이 필요해요"
        !allFieldsFilled -> "정보를 모두 입력해 주세요"
        !uiState.passwordMeetsRequirement -> "비밀번호 조건을 확인해 주세요"
        !uiState.passwordsMatch -> "비밀번호 확인이 필요해요"
        else -> "회원가입 완료"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (buttonEnabled) {
                    Brush.horizontalGradient(listOf(Color(0xFFFFC84F), Color(0xFFFFD96E)))
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFE0D6DA), Color(0xFFEAE3E6)))
                },
            )
            .clickable(
                enabled = buttonEnabled,
                onClick = onSignupClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF6C2C3D),
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = if (buttonEnabled) Color(0xFF6C2C3D) else Color(0xFF9A7586),
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = label,
            modifier = Modifier.padding(start = 8.dp),
            color = if (buttonEnabled) Color(0xFF6C2C3D) else Color(0xFF9A7586),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        )
    }
}

private val SignupBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFAFC8),
        Color(0xFFFFD1C3),
        Color(0xFFFFF4F6),
    ),
)
