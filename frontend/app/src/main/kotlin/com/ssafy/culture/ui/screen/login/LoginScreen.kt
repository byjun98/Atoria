package com.ssafy.culture.ui.screen.login

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.auth.AuthRepository
import com.ssafy.culture.data.auth.AuthTokenStore
import com.ssafy.culture.data.auth.OAuthEvent
import com.ssafy.culture.data.auth.OAuthEventBus
import com.ssafy.culture.ui.component.DecorativeCloud
import com.ssafy.culture.ui.component.ImmersiveNavigationBarEffect
import com.ssafy.culture.ui.layout.CultureHeightClass
import com.ssafy.culture.ui.layout.cultureHeightClass
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val LoginCardShape = RoundedCornerShape(34.dp)
private val LoginFieldShape = RoundedCornerShape(24.dp)

private data class LoginLayoutMetrics(
    val topPadding: Dp,
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val titleBottomSpacing: Dp,
    val screenBottomPadding: Dp,
    val horizontalPadding: Dp,
    val panelTopPadding: Dp,
    val heroContainerHeight: Dp,
    val heroImageHeight: Dp,
    val backgroundHeight: Dp,
    val glowSize: Dp,
    val glowOffsetY: Dp,
    val panelHorizontalPadding: Dp,
    val panelVerticalPadding: Dp,
    val panelTopSpacer: Dp,
    val fieldHeight: Dp,
    val fieldHorizontalPadding: Dp,
    val fieldSpacing: Dp,
    val optionsTopSpacing: Dp,
    val buttonTopSpacing: Dp,
    val buttonHeight: Dp,
    val socialTopSpacing: Dp,
    val socialButtonSize: Dp,
    val socialIconPadding: Dp,
    val signupTopSpacing: Dp,
)

private enum class SocialOAuthProvider(
    val value: String,
) {
    Kakao("kakao"),
    Google("google"),
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isLoginSuccessful: Boolean = false,
    val oauthAuthorizationUrl: String? = null,
    val autoLoginEnabled: Boolean = true,
    val saveLoginEmailEnabled: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authTokenStore: AuthTokenStore,
    private val oauthEventBus: OAuthEventBus,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        LoginUiState(
            email = authTokenStore.getSavedLoginEmail().orEmpty(),
            autoLoginEnabled = authTokenStore.isAutoLoginEnabled(),
            saveLoginEmailEnabled = authTokenStore.isSaveLoginEmailEnabled(),
        ),
    )
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            oauthEventBus.events.collect { event ->
                when (event) {
                    is OAuthEvent.Success -> _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isLoginSuccessful = true,
                            errorMessage = null,
                        )
                    }

                    is OAuthEvent.Failure -> _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            errorMessage = event.message,
                        )
                    }
                }
            }
        }
    }

    fun onEmailChange(email: String) {
        if (_uiState.value.saveLoginEmailEnabled) {
            authTokenStore.updateSavedLoginEmail(email)
        }
        _uiState.update { state ->
            state.copy(email = email, errorMessage = null)
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update { state ->
            state.copy(password = password, errorMessage = null)
        }
    }

    fun onAutoLoginEnabledChange(isEnabled: Boolean) {
        authTokenStore.setAutoLoginEnabled(isEnabled)
        _uiState.update { state ->
            state.copy(autoLoginEnabled = isEnabled)
        }
    }

    fun onSaveLoginEmailEnabledChange(isEnabled: Boolean) {
        val currentEmail = _uiState.value.email
        authTokenStore.setSaveLoginEmailEnabled(isEnabled)
        if (isEnabled) {
            authTokenStore.updateSavedLoginEmail(currentEmail)
        }
        _uiState.update { state ->
            state.copy(saveLoginEmailEnabled = isEnabled)
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.email.isBlank() || state.password.isBlank()) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = "이메일과 비밀번호를 입력해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            authTokenStore.setAutoLoginEnabled(state.autoLoginEnabled)
            if (state.saveLoginEmailEnabled) {
                authTokenStore.updateSavedLoginEmail(state.email)
            } else {
                authTokenStore.setSaveLoginEmailEnabled(false)
            }
            runCatching {
                authRepository.login(
                    email = state.email,
                    password = state.password,
                )
            }.onSuccess {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        isLoginSuccessful = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "로그인에 실패했습니다.",
                    )
                }
            }
        }
    }

    fun startOAuthLogin(provider: String) {
        val state = _uiState.value
        if (state.isLoading) return
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }
            authTokenStore.setAutoLoginEnabled(state.autoLoginEnabled)
            runCatching {
                authRepository.getOAuthAuthorizationUrl(provider)
            }.onSuccess { authorizationUrl ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        oauthAuthorizationUrl = authorizationUrl,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "소셜 로그인을 시작하지 못했습니다.",
                    )
                }
            }
        }
    }

    fun consumeOAuthAuthorizationUrl() {
        _uiState.update { state ->
            state.copy(oauthAuthorizationUrl = null)
        }
    }

    fun onOAuthLaunchFailed() {
        _uiState.update { state ->
            state.copy(errorMessage = "브라우저를 열 수 없습니다.")
        }
    }

    fun consumeLoginSuccess() {
        _uiState.update { state ->
            state.copy(isLoginSuccessful = false)
        }
    }
}

/*
 * Optional asset names you can add later under app/src/main/res/drawable:
 * - login_background_top.png
 * - login_hero_scene.png
 * - ic_kakao_logo.png
 * - ic_google_logo.png
 */

@Composable
fun LoginRoute(
    onLoginSuccess: () -> Unit,
    onOpenSignup: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(uiState.isLoginSuccessful) {
        if (uiState.isLoginSuccessful) {
            viewModel.consumeLoginSuccess()
            onLoginSuccess()
        }
    }

    LaunchedEffect(uiState.oauthAuthorizationUrl) {
        val authorizationUrl = uiState.oauthAuthorizationUrl ?: return@LaunchedEffect
        viewModel.consumeOAuthAuthorizationUrl()
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authorizationUrl)))
        } catch (_: ActivityNotFoundException) {
            viewModel.onOAuthLaunchFailed()
        }
    }

    LoginScreen(
        uiState = uiState,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onAutoLoginEnabledChange = viewModel::onAutoLoginEnabledChange,
        onSaveLoginEmailEnabledChange = viewModel::onSaveLoginEmailEnabledChange,
        onLoginClick = viewModel::login,
        onKakaoLoginClick = { viewModel.startOAuthLogin(SocialOAuthProvider.Kakao.value) },
        onGoogleLoginClick = { viewModel.startOAuthLogin(SocialOAuthProvider.Google.value) },
        onOpenSignup = onOpenSignup,
    )
}

@Composable
private fun LoginScreen(
    uiState: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAutoLoginEnabledChange: (Boolean) -> Unit,
    onSaveLoginEmailEnabledChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onKakaoLoginClick: () -> Unit,
    onGoogleLoginClick: () -> Unit,
    onOpenSignup: () -> Unit,
) {
    ImmersiveNavigationBarEffect()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFB0C7),
                        Color(0xFFFFCFBF),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
            .imePadding(),
    ) {
        val metrics = loginLayoutMetrics(maxHeight)

        LoginSkyBackground(
            backgroundHeight = metrics.backgroundHeight,
            glowSize = metrics.glowSize,
            glowOffsetY = metrics.glowOffsetY,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = metrics.screenBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(metrics.topPadding))
            StickerTitle(
                text = "로그인",
                fontSize = metrics.titleFontSize,
                lineHeight = metrics.titleLineHeight,
            )
            Spacer(modifier = Modifier.height(metrics.titleBottomSpacing))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(horizontal = metrics.horizontalPadding),
            ) {
                LoginPanel(
                    email = uiState.email,
                    password = uiState.password,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    autoLoginEnabled = uiState.autoLoginEnabled,
                    saveLoginEmailEnabled = uiState.saveLoginEmailEnabled,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onAutoLoginEnabledChange = onAutoLoginEnabledChange,
                    onSaveLoginEmailEnabledChange = onSaveLoginEmailEnabledChange,
                    onLoginClick = onLoginClick,
                    onKakaoLoginClick = onKakaoLoginClick,
                    onGoogleLoginClick = onGoogleLoginClick,
                    onOpenSignup = onOpenSignup,
                    metrics = metrics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.panelTopPadding),
                )

                HeroScene(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(metrics.heroContainerHeight),
                    imageHeight = metrics.heroImageHeight,
                )
            }
        }
    }
}

private fun loginLayoutMetrics(maxHeight: Dp): LoginLayoutMetrics =
    when (cultureHeightClass(maxHeight)) {
        CultureHeightClass.Compact -> LoginLayoutMetrics(
            topPadding = 10.dp,
            titleFontSize = 48.sp,
            titleLineHeight = 54.sp,
            titleBottomSpacing = 4.dp,
            screenBottomPadding = 12.dp,
            horizontalPadding = 16.dp,
            panelTopPadding = 200.dp,
            heroContainerHeight = 228.dp,
            heroImageHeight = 228.dp,
            backgroundHeight = 382.dp,
            glowSize = 360.dp,
            glowOffsetY = 54.dp,
            panelHorizontalPadding = 22.dp,
            panelVerticalPadding = 16.dp,
            panelTopSpacer = 8.dp,
            fieldHeight = 62.dp,
            fieldHorizontalPadding = 24.dp,
            fieldSpacing = 10.dp,
            optionsTopSpacing = 8.dp,
            buttonTopSpacing = 12.dp,
            buttonHeight = 62.dp,
            socialTopSpacing = 12.dp,
            socialButtonSize = 56.dp,
            socialIconPadding = 14.dp,
            signupTopSpacing = 8.dp,
        )
        CultureHeightClass.Medium -> LoginLayoutMetrics(
            topPadding = 18.dp,
            titleFontSize = 54.sp,
            titleLineHeight = 60.sp,
            titleBottomSpacing = 10.dp,
            screenBottomPadding = 20.dp,
            horizontalPadding = 18.dp,
            panelTopPadding = 224.dp,
            heroContainerHeight = 256.dp,
            heroImageHeight = 256.dp,
            backgroundHeight = 410.dp,
            glowSize = 392.dp,
            glowOffsetY = 62.dp,
            panelHorizontalPadding = 24.dp,
            panelVerticalPadding = 18.dp,
            panelTopSpacer = 10.dp,
            fieldHeight = 66.dp,
            fieldHorizontalPadding = 26.dp,
            fieldSpacing = 12.dp,
            optionsTopSpacing = 10.dp,
            buttonTopSpacing = 12.dp,
            buttonHeight = 66.dp,
            socialTopSpacing = 14.dp,
            socialButtonSize = 60.dp,
            socialIconPadding = 15.dp,
            signupTopSpacing = 10.dp,
        )
        CultureHeightClass.Expanded -> LoginLayoutMetrics(
            topPadding = 30.dp,
            titleFontSize = 60.sp,
            titleLineHeight = 66.sp,
            titleBottomSpacing = 18.dp,
            screenBottomPadding = 32.dp,
            horizontalPadding = 18.dp,
            panelTopPadding = 256.dp,
            heroContainerHeight = 292.dp,
            heroImageHeight = 312.dp,
            backgroundHeight = 430.dp,
            glowSize = 420.dp,
            glowOffsetY = 72.dp,
            panelHorizontalPadding = 26.dp,
            panelVerticalPadding = 20.dp,
            panelTopSpacer = 12.dp,
            fieldHeight = 72.dp,
            fieldHorizontalPadding = 28.dp,
            fieldSpacing = 14.dp,
            optionsTopSpacing = 12.dp,
            buttonTopSpacing = 14.dp,
            buttonHeight = 74.dp,
            socialTopSpacing = 18.dp,
            socialButtonSize = 64.dp,
            socialIconPadding = 16.dp,
            signupTopSpacing = 14.dp,
        )
    }

@Composable
private fun LoginSkyBackground(
    backgroundHeight: Dp,
    glowSize: Dp,
    glowOffsetY: Dp,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(glowSize)
                .align(Alignment.TopCenter)
                .offset(y = glowOffsetY)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFF6CE).copy(alpha = 0.95f),
                            Color(0xFFFFF6CE).copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                )
                .graphicsLayer(alpha = 0.95f),
        )

        OptionalDrawable(
            name = "login_background_top",
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(backgroundHeight)
                .softBottomFade(),
            contentScale = ContentScale.Crop,
        ) {
            DecorativeCloud(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 18.dp, y = 88.dp)
                    .size(width = 150.dp, height = 84.dp),
                colors = listOf(Color(0xFFFFD8E8), Color(0xFFFFF8FC)),
            )
            DecorativeCloud(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-20).dp, y = 108.dp)
                    .size(width = 168.dp, height = 92.dp),
                colors = listOf(Color(0xFFFFE2F0), Color(0xFFFFFFFF)),
            )
            DecorativeCloud(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-12).dp, y = 52.dp)
                    .size(width = 196.dp, height = 110.dp),
                colors = listOf(Color(0xFFFFF2F8), Color(0xFFFFFFFF)),
            )
            RainbowArc(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-28).dp, y = 16.dp)
                    .size(186.dp),
            )
            SparkleCluster(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(320.dp),
            )
        }
    }
}

@Composable
private fun HeroScene(
    modifier: Modifier = Modifier,
    imageHeight: Dp,
) {
    Box(modifier = modifier) {
        OptionalDrawable(
            name = "login_hero_scene",
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.86f)
                .height(imageHeight),
            contentScale = ContentScale.Fit,
        ) {
            HeroSceneFallback()
        }
    }
}

@Composable
private fun BoxScope.HeroSceneFallback() {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(300.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFBDF),
                        Color(0xFFFFF4BC).copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
                shape = CircleShape,
            ),
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .offset(y = 42.dp)
            .size(width = 128.dp, height = 90.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFEE839E)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 54.dp, height = 58.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Color(0xFFF58BA7)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
                .size(width = 8.dp, height = 42.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFD45D7E)),
        )
    }

    BookIllustration(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth(0.96f)
            .height(120.dp),
    )

    MascotFallback(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(y = 28.dp),
    )

    SparkleCluster(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(240.dp),
    )
}

@Composable
private fun LoginPanel(
    email: String,
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    autoLoginEnabled: Boolean,
    saveLoginEmailEnabled: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onAutoLoginEnabledChange: (Boolean) -> Unit,
    onSaveLoginEmailEnabledChange: (Boolean) -> Unit,
    onLoginClick: () -> Unit,
    onKakaoLoginClick: () -> Unit,
    onGoogleLoginClick: () -> Unit,
    onOpenSignup: () -> Unit,
    metrics: LoginLayoutMetrics,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = LoginCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 18.dp,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.panelHorizontalPadding,
                    vertical = metrics.panelVerticalPadding,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(metrics.panelTopSpacer))
            DreamyInputField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = "이메일",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                height = metrics.fieldHeight,
                horizontalPadding = metrics.fieldHorizontalPadding,
            )
            Spacer(modifier = Modifier.height(metrics.fieldSpacing))
            DreamyInputField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = "비밀번호",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                isPassword = true,
                height = metrics.fieldHeight,
                horizontalPadding = metrics.fieldHorizontalPadding,
            )
            Spacer(modifier = Modifier.height(metrics.optionsTopSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LoginOptionCheckboxRow(
                    label = "\uC544\uC774\uB514 \uC800\uC7A5",
                    checked = saveLoginEmailEnabled,
                    enabled = !isLoading,
                    onCheckedChange = onSaveLoginEmailEnabledChange,
                    modifier = Modifier.weight(1f),
                )
                LoginOptionCheckboxRow(
                    label = "\uC790\uB3D9 \uB85C\uADF8\uC778",
                    checked = autoLoginEnabled,
                    enabled = !isLoading,
                    onCheckedChange = onAutoLoginEnabledChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(metrics.buttonTopSpacing))
            GradientLoginButton(
                text = if (isLoading) "로그인 중" else "로그인",
                onClick = onLoginClick,
                enabled = !isLoading,
                height = metrics.buttonHeight,
            )
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(metrics.socialTopSpacing))
            SocialLoginRow(
                enabled = !isLoading,
                onKakaoLoginClick = onKakaoLoginClick,
                onGoogleLoginClick = onGoogleLoginClick,
                buttonSize = metrics.socialButtonSize,
                iconPadding = metrics.socialIconPadding,
            )
            Spacer(modifier = Modifier.height(metrics.signupTopSpacing))
            val signupPrompt = buildAnnotatedString {
                append("아직 계정이 없으신가요? ")
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        textDecoration = TextDecoration.Underline,
                    ),
                ) {
                    append("회원가입")
                }
            }
            Text(
                text = signupPrompt,
                modifier = Modifier
                    .tossClickable(
                        pressedScale = CultureMotion.SubtlePressedScale,
                        onClick = onOpenSignup,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = "회원가입 화면으로 이동"
                    },
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Composable
private fun LoginOptionCheckboxRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                enabled = enabled,
                role = Role.Checkbox,
                onClick = { onCheckedChange(!checked) },
            )
            .padding(start = 4.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun DreamyInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    isPassword: Boolean = false,
    height: Dp,
    horizontalPadding: Dp,
) {
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface,
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .shadowTinted(
                shape = LoginFieldShape,
                shadowColor = Color(0x33C46B8B),
            )
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFCFD),
                        Color(0xFFFFF2F6),
                    ),
                ),
                shape = LoginFieldShape,
            )
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                shape = LoginFieldShape,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                style = textStyle,
                color = Color(0xFF84727C),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = textStyle,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
            visualTransformation = if (isPassword) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            cursorBrush = Brush.verticalGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primaryContainer,
                ),
            ),
        )
    }
}

@Composable
private fun GradientLoginButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    height: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .shadowTinted(
                shape = RoundedCornerShape(24.dp),
                shadowColor = Color(0x33B84874),
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF4B85),
                        Color(0xFFFF5D94),
                    ),
                ),
            )
            .tossClickable(
                enabled = enabled,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (enabled) {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    shadow = Shadow(
                        color = Color(0x803E1A2A),
                        blurRadius = 12f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 3f),
                    ),
                ),
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = text,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
    }
}

@Composable
private fun SocialLoginRow(
    enabled: Boolean,
    onKakaoLoginClick: () -> Unit,
    onGoogleLoginClick: () -> Unit,
    buttonSize: Dp,
    iconPadding: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialButton(
            resourceName = "ic_kakao_logo",
            contentDescription = "카카오 로그인",
            enabled = enabled,
            size = buttonSize,
            iconPadding = iconPadding,
            onClick = onKakaoLoginClick,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFFEE500)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "K",
                    color = Color(0xFF3A1E1E),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                )
            }
        }

        SocialButton(
            resourceName = "ic_google_logo",
            contentDescription = "구글 로그인",
            enabled = enabled,
            size = buttonSize,
            iconPadding = iconPadding,
            onClick = onGoogleLoginClick,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE6D8DE), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "G",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color(0xFF4285F4),
                )
            }
        }
    }
}

@Composable
private fun SocialButton(
    resourceName: String,
    contentDescription: String,
    enabled: Boolean,
    size: Dp,
    iconPadding: Dp,
    onClick: () -> Unit,
    fallback: @Composable BoxScope.() -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(size)
            .tossClickable(
                enabled = enabled,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = Color.White,
        shadowElevation = 10.dp,
    ) {
        OptionalDrawable(
            name = resourceName,
            modifier = Modifier
                .fillMaxSize()
                .padding(iconPadding),
            contentScale = ContentScale.Fit,
            contentDescription = contentDescription,
        ) {
            fallback()
        }
    }
}

@Composable
private fun StickerTitle(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val outlineColor = Color(0xFF91556E)
    val highlightColor = Color(0xFFFFDCE7)
    val fillColor = Color(0xFFFFF6FA)
    val titleStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.ExtraBold,
        shadow = Shadow(
            color = Color(0x55FFFFFF),
            blurRadius = 10f,
            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
        ),
    )

    Box {
        val offsets = listOf(
            DpOffset((-2).dp, 0.dp),
            DpOffset(2.dp, 0.dp),
            DpOffset(0.dp, (-2).dp),
            DpOffset(0.dp, 2.dp),
            DpOffset(2.dp, 2.dp),
            DpOffset((-2).dp, 2.dp),
            DpOffset(2.dp, (-2).dp),
            DpOffset((-2).dp, (-2).dp),
        )

        offsets.forEach { offset ->
            Text(
                text = text,
                modifier = Modifier.offset(offset.x, offset.y),
                style = titleStyle,
                color = outlineColor,
            )
        }

        Text(
            text = text,
            modifier = Modifier.offset(y = 3.dp),
            style = titleStyle,
            color = highlightColor.copy(alpha = 0.9f),
        )

        Text(
            text = text,
            style = titleStyle,
            color = fillColor,
        )
    }
}

@Composable
private fun RainbowArc(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val colors = listOf(
            Color(0xFFFF7EAA),
            Color(0xFFFFC16E),
            Color(0xFFFFED83),
            Color(0xFFA7EC8B),
            Color(0xFF86D4FF),
            Color(0xFFAD90FF),
        )
        val stroke = size.minDimension * 0.06f
        colors.forEachIndexed { index, color ->
            drawArc(
                color = color.copy(alpha = 0.92f),
                startAngle = 208f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(index * stroke * 0.72f, index * stroke * 0.72f),
                size = androidx.compose.ui.geometry.Size(
                    size.width - index * stroke * 1.44f,
                    size.height - index * stroke * 1.44f,
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun SparkleCluster(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Sparkle(Modifier.align(Alignment.TopCenter).offset(x = (-74).dp, y = 110.dp), 28.dp)
        Sparkle(Modifier.align(Alignment.TopCenter).offset(x = 68.dp, y = 124.dp), 22.dp)
        Sparkle(Modifier.align(Alignment.TopCenter).offset(x = 120.dp, y = 146.dp), 18.dp)
        Sparkle(Modifier.align(Alignment.TopCenter).offset(x = (-126).dp, y = 150.dp), 22.dp)
        Sparkle(Modifier.align(Alignment.TopCenter).offset(x = 0.dp, y = 100.dp), 18.dp)
    }
}

@Composable
private fun Sparkle(
    modifier: Modifier,
    size: androidx.compose.ui.unit.Dp,
) {
    androidx.compose.material3.Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = Color(0xFFFFD85C),
        modifier = modifier.size(size),
    )
}

@Composable
private fun MascotFallback(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(210.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-20).dp, y = 12.dp)
                .size(150.dp)
                .rotate((-18f))
                .clip(RoundedCornerShape(58.dp))
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFF17CA7),
                            Color(0xFFFFB8CE),
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(132.dp)
                .clip(RoundedCornerShape(48.dp))
                .background(Color(0xFFFFFCFB)),
        )
        FaceFeature(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-22).dp, y = (-4).dp),
        )
        FaceFeature(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 22.dp, y = (-4).dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 26.dp)
                .width(34.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF6B2037)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = (-36).dp, y = 24.dp)
                .size(20.dp)
                .background(Color(0xFFFFB4BE), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 36.dp, y = 24.dp)
                .size(20.dp)
                .background(Color(0xFFFFB4BE), CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 62.dp, y = 18.dp)
                .rotate((-20f))
                .size(width = 62.dp, height = 16.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xFFE76896)),
        )
        androidx.compose.material3.Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = Color(0xFFFFA8C7),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 96.dp, y = (-14).dp)
                .size(42.dp),
        )
    }
}

@Composable
private fun FaceFeature(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = 18.dp, height = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF8C284C)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(6.dp)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun BookIllustration(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.52f)
                .height(64.dp)
                .rotate((-7f))
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFF9DC), Color(0xFFFFEBA3)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(0.52f)
                .height(64.dp)
                .rotate(7f)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFF9DC), Color(0xFFFFEBA3)),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(54.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(Color(0xFFF3A548)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 12.dp)
                .fillMaxWidth()
                .height(18.dp)
                .clip(RoundedCornerShape(50.dp))
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFBB476F), Color(0xFFE4708F), Color(0xFFBB476F)),
                    ),
                ),
        )
    }
}

@Composable
private fun OptionalDrawable(
    name: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
    fallback: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val resourceId = remember(name, context.packageName) {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    }

    Box(modifier = modifier) {
        if (resourceId != 0) {
            Image(
                painter = painterResource(id = resourceId),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            fallback()
        }
    }
}

private fun Modifier.shadowTinted(
    shape: RoundedCornerShape,
    shadowColor: Color,
): Modifier = this.graphicsLayer {
    this.shape = shape
    this.clip = false
    shadowElevation = 28.dp.toPx()
    ambientShadowColor = shadowColor
    spotShadowColor = shadowColor
}

private fun Modifier.softBottomFade(): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.White,
                    0.74f to Color.White,
                    1f to Color.Transparent,
                ),
                startY = 0f,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
