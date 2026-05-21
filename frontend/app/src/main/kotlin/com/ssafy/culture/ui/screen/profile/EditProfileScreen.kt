package com.ssafy.culture.ui.screen.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.user.UserRepository
import com.ssafy.culture.domain.model.PasswordChange
import com.ssafy.culture.domain.model.UserProfileUpdate
import com.ssafy.culture.ui.motion.tossClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val nickname: String = "",
    val email: String = "",
    val currentPassword: String = "",
    val newPassword: String = "",
    val newPasswordConfirm: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
) {
    val isChangingPassword: Boolean
        get() = currentPassword.isNotBlank() || newPassword.isNotBlank() || newPasswordConfirm.isNotBlank()

    val passwordsMatch: Boolean
        get() = newPassword.isNotBlank() && newPassword == newPasswordConfirm

    val passwordMessage: String?
        get() = when {
            !isChangingPassword -> null
            newPasswordConfirm.isBlank() -> "새 비밀번호 확인을 입력해 주세요."
            passwordsMatch -> "새 비밀번호가 일치합니다."
            else -> "새 비밀번호가 일치하지 않습니다."
        }

    val canSubmit: Boolean
        get() = nickname.isNotBlank() &&
            email.isNotBlank() &&
            !isLoading &&
            !isSaving &&
            (!isChangingPassword || currentPassword.isNotBlank() && passwordsMatch)
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = _uiState.asStateFlow()

    init {
        loadUser()
    }

    fun updateNickname(nickname: String) {
        _uiState.update { state ->
            state.copy(nickname = nickname, errorMessage = null)
        }
    }

    fun updateEmail(email: String) {
        _uiState.update { state ->
            state.copy(email = email, errorMessage = null)
        }
    }

    fun updateCurrentPassword(currentPassword: String) {
        _uiState.update { state ->
            state.copy(currentPassword = currentPassword, errorMessage = null)
        }
    }

    fun updateNewPassword(newPassword: String) {
        _uiState.update { state ->
            state.copy(newPassword = newPassword, errorMessage = null)
        }
    }

    fun updateNewPasswordConfirm(newPasswordConfirm: String) {
        _uiState.update { state ->
            state.copy(newPasswordConfirm = newPasswordConfirm, errorMessage = null)
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (!state.canSubmit) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = "입력 내용을 다시 확인해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isSaving = true, errorMessage = null)
            }
            runCatching {
                userRepository.updateProfile(
                    UserProfileUpdate(
                        nickname = state.nickname.trim(),
                        email = state.email.trim(),
                    ),
                )
                if (state.isChangingPassword) {
                    userRepository.changePassword(
                        PasswordChange(
                            currentPassword = state.currentPassword,
                            newPassword = state.newPassword,
                        ),
                    )
                }
            }.onSuccess {
                _uiState.update { currentState ->
                    currentState.copy(
                        isSaving = false,
                        isSaved = true,
                    )
                }
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        isSaving = false,
                        errorMessage = "프로필을 저장하지 못했어요.",
                    )
                }
            }
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            runCatching {
                userRepository.refreshCurrentUser()
            }.onSuccess { user ->
                _uiState.update { state ->
                    state.copy(
                        nickname = user.nickname,
                        email = user.email,
                        isLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "사용자 정보를 불러오지 못했어요.",
                    )
                }
            }
        }
    }
}

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onBack()
        }
    }

    ProfileSubPageScaffold(
        title = "내 정보 수정",
        onBack = onBack,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = Color.White,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFFF7BA8), Color(0xFFFFC2D5))))
                        .border(5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(62.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                ProfileInputField(
                    label = "닉네임",
                    value = uiState.nickname,
                    onValueChange = viewModel::updateNickname,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInputField(
                    label = "이메일",
                    value = uiState.email,
                    onValueChange = viewModel::updateEmail,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInputField(
                    label = "현재 비밀번호",
                    value = uiState.currentPassword,
                    onValueChange = viewModel::updateCurrentPassword,
                    isPassword = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInputField(
                    label = "새 비밀번호",
                    value = uiState.newPassword,
                    onValueChange = viewModel::updateNewPassword,
                    isPassword = true,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ProfileInputField(
                    label = "새 비밀번호 확인",
                    value = uiState.newPasswordConfirm,
                    onValueChange = viewModel::updateNewPasswordConfirm,
                    isPassword = true,
                )
                val passwordMessage = uiState.passwordMessage
                if (passwordMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = passwordMessage,
                        modifier = Modifier.fillMaxWidth(),
                        color = if (uiState.passwordsMatch) Color(0xFF2E9D55) else Color(0xFFE03B50),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                val errorMessage = uiState.errorMessage
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE03B50),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
                Spacer(modifier = Modifier.height(22.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (uiState.canSubmit) 1f else 0.58f)
                        .tossClickable(enabled = uiState.canSubmit, onClick = viewModel::saveProfile),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 8.dp,
                ) {
                    Text(
                        text = if (uiState.isSaving) "저장 중..." else "저장하기",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isPassword: Boolean = false,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFFFF7FA))
                .border(1.dp, Color(0xFFFFC7DA), RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                ),
                singleLine = true,
                visualTransformation = if (isPassword) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )
        }
    }
}

@Composable
internal fun ProfileSubPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .size(420.dp)
                .align(Alignment.TopCenter)
                .offset(y = 60.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFFFFF7C9).copy(alpha = 0.95f),
                            Color(0xFFFFF7C9).copy(alpha = 0.18f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(top = 26.dp, bottom = 34.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            content()
        }
    }
}
