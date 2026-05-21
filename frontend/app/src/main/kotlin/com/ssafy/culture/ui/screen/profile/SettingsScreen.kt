package com.ssafy.culture.ui.screen.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Recommend
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.notification.NotificationRepository
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.preferences.RouteTrackingMode
import com.ssafy.culture.data.route.RouteTracker
import com.ssafy.culture.data.user.UserRepository
import com.ssafy.culture.ui.screen.route.RouteTrackingModeDialog
import com.ssafy.culture.domain.model.NotificationItem
import com.ssafy.culture.domain.model.NotificationSettings
import com.ssafy.culture.domain.model.NotificationSummary
import com.ssafy.culture.domain.model.NotificationType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val notificationSettings: NotificationSettings = NotificationSettings(),
    val notifications: List<NotificationItem> = emptyList(),
    val summary: NotificationSummary = NotificationSummary(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isDeleteAccountDialogVisible: Boolean = false,
    val deleteAccountPassword: String = "",
    val isDeletingAccount: Boolean = false,
    val isAccountDeleted: Boolean = false,
    val deleteAccountErrorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val userRepository: UserRepository,
    private val appPreferenceStore: AppPreferenceStore,
    private val routeTracker: RouteTracker,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val captureTimerEnabled: StateFlow<Boolean> = appPreferenceStore.captureTimerEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferenceStore.DefaultCaptureTimerEnabled,
    )

    val captureTimerSeconds: StateFlow<Int> = appPreferenceStore.captureTimerSeconds.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppPreferenceStore.DefaultCaptureTimerSeconds,
    )

    val routeTrackingMode: StateFlow<RouteTrackingMode> = appPreferenceStore.routeTrackingMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RouteTrackingMode.Default,
    )

    private val _isRouteTrackingDialogVisible = MutableStateFlow(false)
    val isRouteTrackingDialogVisible: StateFlow<Boolean> = _isRouteTrackingDialogVisible.asStateFlow()

    fun showRouteTrackingModeDialog() {
        _isRouteTrackingDialogVisible.value = true
    }

    fun dismissRouteTrackingModeDialog() {
        _isRouteTrackingDialogVisible.value = false
    }

    fun selectRouteTrackingMode(mode: RouteTrackingMode) {
        _isRouteTrackingDialogVisible.value = false
        viewModelScope.launch {
            runCatching { appPreferenceStore.setRouteTrackingMode(mode) }
            if (mode == RouteTrackingMode.Off) {
                routeTracker.stop()
            } else {
                routeTracker.refreshModeIfActive()
            }
        }
    }

    fun setCaptureTimerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferenceStore.setCaptureTimerEnabled(enabled)
        }
    }

    fun setCaptureTimerSeconds(seconds: Int) {
        viewModelScope.launch {
            appPreferenceStore.setCaptureTimerSeconds(seconds)
        }
    }

    init {
        loadSettings()
        refreshNotifications()
    }

    fun setPushEnabled(enabled: Boolean) {
        updateNotificationSettings { settings ->
            settings.copy(pushEnabled = enabled)
        }
    }

    fun setEventEnabled(enabled: Boolean) {
        updateNotificationSettings { settings ->
            settings.copy(eventEnabled = enabled)
        }
    }

    fun setRecommendEnabled(enabled: Boolean) {
        updateNotificationSettings { settings ->
            settings.copy(recommendEnabled = enabled)
        }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            loadNotifications(shouldShowLoading = true)
        }
    }

    fun markAsRead(notificationId: String) {
        viewModelScope.launch {
            runCatching {
                notificationRepository.markAsRead(notificationId)
                loadNotifications(shouldShowLoading = false)
            }.onFailure {
                showNotificationError()
            }
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            runCatching {
                notificationRepository.markAllAsRead()
                loadNotifications(shouldShowLoading = false)
            }.onFailure {
                showNotificationError()
            }
        }
    }

    fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            runCatching {
                notificationRepository.deleteNotification(notificationId)
                loadNotifications(shouldShowLoading = false)
            }.onFailure {
                showNotificationError()
            }
        }
    }

    fun showDeleteAccountDialog() {
        _uiState.update { state ->
            state.copy(
                isDeleteAccountDialogVisible = true,
                deleteAccountPassword = "",
                deleteAccountErrorMessage = null,
            )
        }
    }

    fun dismissDeleteAccountDialog() {
        if (_uiState.value.isDeletingAccount) return
        _uiState.update { state ->
            state.copy(
                isDeleteAccountDialogVisible = false,
                deleteAccountPassword = "",
                deleteAccountErrorMessage = null,
            )
        }
    }

    fun updateDeleteAccountPassword(password: String) {
        _uiState.update { state ->
            state.copy(
                deleteAccountPassword = password,
                deleteAccountErrorMessage = null,
            )
        }
    }

    fun deleteAccount() {
        val password = _uiState.value.deleteAccountPassword
        if (password.isBlank()) {
            _uiState.update { state ->
                state.copy(deleteAccountErrorMessage = "비밀번호를 입력해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isDeletingAccount = true, deleteAccountErrorMessage = null)
            }
            runCatching {
                userRepository.deleteAccount(password = password)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isDeletingAccount = false,
                        isDeleteAccountDialogVisible = false,
                        deleteAccountPassword = "",
                        isAccountDeleted = true,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isDeletingAccount = false,
                        deleteAccountErrorMessage = throwable.message ?: "회원탈퇴에 실패했습니다.",
                    )
                }
            }
        }
    }

    fun consumeAccountDeleted() {
        _uiState.update { state ->
            state.copy(isAccountDeleted = false)
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            runCatching {
                notificationRepository.getNotificationSettings()
            }.onSuccess { settings ->
                _uiState.update { state ->
                    state.copy(notificationSettings = settings)
                }
            }
        }
    }

    private fun updateNotificationSettings(
        transform: (NotificationSettings) -> NotificationSettings,
    ) {
        val nextSettings = transform(_uiState.value.notificationSettings)
        _uiState.update { state ->
            state.copy(notificationSettings = nextSettings)
        }
        viewModelScope.launch {
            runCatching {
                notificationRepository.updateNotificationSettings(nextSettings)
            }.onFailure {
                _uiState.update { state ->
                    state.copy(errorMessage = "알림 설정을 저장하지 못했어요.")
                }
            }
        }
    }

    private suspend fun loadNotifications(shouldShowLoading: Boolean) {
        if (shouldShowLoading) {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
        }
        runCatching {
            notificationRepository.getNotifications()
        }.onSuccess { notifications ->
            _uiState.update { state ->
                state.copy(
                    notifications = notifications,
                    summary = notifications.toSummary(),
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    errorMessage = "알림을 불러오지 못했어요.",
                )
            }
        }
    }

    private fun showNotificationError() {
        _uiState.update { state ->
            state.copy(errorMessage = "알림 상태를 변경하지 못했어요.")
        }
    }
}

@Composable
fun SettingsRoute(
    darkModeEnabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureTimerEnabled by viewModel.captureTimerEnabled.collectAsStateWithLifecycle()
    val captureTimerSeconds by viewModel.captureTimerSeconds.collectAsStateWithLifecycle()
    val routeTrackingMode by viewModel.routeTrackingMode.collectAsStateWithLifecycle()
    val isRouteTrackingDialogVisible by viewModel.isRouteTrackingDialogVisible.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isAccountDeleted) {
        if (uiState.isAccountDeleted) {
            viewModel.consumeAccountDeleted()
            onAccountDeleted()
        }
    }

    SettingsScreen(
        uiState = uiState,
        darkModeEnabled = darkModeEnabled,
        onDarkModeChange = onDarkModeChange,
        captureTimerEnabled = captureTimerEnabled,
        captureTimerSeconds = captureTimerSeconds,
        routeTrackingMode = routeTrackingMode,
        onRouteTrackingClick = viewModel::showRouteTrackingModeDialog,
        onCaptureTimerEnabledChange = viewModel::setCaptureTimerEnabled,
        onCaptureTimerSecondsChange = viewModel::setCaptureTimerSeconds,
        onBack = onBack,
        onPushEnabledChange = viewModel::setPushEnabled,
        onEventEnabledChange = viewModel::setEventEnabled,
        onRecommendEnabledChange = viewModel::setRecommendEnabled,
        onMarkAsRead = viewModel::markAsRead,
        onMarkAllAsRead = viewModel::markAllAsRead,
        onDeleteNotification = viewModel::deleteNotification,
        onShowDeleteAccountDialog = viewModel::showDeleteAccountDialog,
        onDismissDeleteAccountDialog = viewModel::dismissDeleteAccountDialog,
        onDeleteAccountPasswordChange = viewModel::updateDeleteAccountPassword,
        onConfirmDeleteAccount = viewModel::deleteAccount,
    )
    if (isRouteTrackingDialogVisible) {
        RouteTrackingModeDialog(
            onSelect = viewModel::selectRouteTrackingMode,
            onDismiss = viewModel::dismissRouteTrackingModeDialog,
        )
    }
}

@Composable
private fun SettingsScreen(
    uiState: SettingsUiState,
    darkModeEnabled: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    captureTimerEnabled: Boolean,
    captureTimerSeconds: Int,
    routeTrackingMode: RouteTrackingMode,
    onRouteTrackingClick: () -> Unit,
    onCaptureTimerEnabledChange: (Boolean) -> Unit,
    onCaptureTimerSecondsChange: (Int) -> Unit,
    onBack: () -> Unit,
    onPushEnabledChange: (Boolean) -> Unit,
    onEventEnabledChange: (Boolean) -> Unit,
    onRecommendEnabledChange: (Boolean) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onShowDeleteAccountDialog: () -> Unit,
    onDismissDeleteAccountDialog: () -> Unit,
    onDeleteAccountPasswordChange: (String) -> Unit,
    onConfirmDeleteAccount: () -> Unit,
) {
    val settings = uiState.notificationSettings

    ProfileSubPageScaffold(
        title = "설정",
        onBack = onBack,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Text(
                    text = "알림설정",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsDivider()
                SettingsToggleRow(
                    title = "푸시알림",
                    icon = Icons.Rounded.Notifications,
                    checked = settings.pushEnabled,
                    onCheckedChange = onPushEnabledChange,
                )
                SettingsToggleRow(
                    title = "이벤트 알림",
                    icon = Icons.Rounded.Event,
                    checked = settings.eventEnabled,
                    onCheckedChange = onEventEnabledChange,
                )
                SettingsToggleRow(
                    title = "추천 알림",
                    icon = Icons.Rounded.Recommend,
                    checked = settings.recommendEnabled,
                    onCheckedChange = onRecommendEnabledChange,
                )
                SettingsToggleRow(
                    title = "다크모드",
                    icon = Icons.Rounded.DarkMode,
                    checked = darkModeEnabled,
                    onCheckedChange = onDarkModeChange,
                )
                SettingsDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "촬영 설정",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsDivider()
                SettingsToggleRow(
                    title = "촬영 타이머",
                    icon = Icons.Rounded.CameraAlt,
                    checked = captureTimerEnabled,
                    onCheckedChange = onCaptureTimerEnabledChange,
                )
                CaptureTimerSliderRow(
                    enabled = captureTimerEnabled,
                    seconds = captureTimerSeconds,
                    onSecondsChange = onCaptureTimerSecondsChange,
                )
                SettingsDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "위치 기록",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = Modifier.height(16.dp))
                SettingsDivider()
                RouteTrackingModeRow(
                    mode = routeTrackingMode,
                    onClick = onRouteTrackingClick,
                )
                NotificationListSection(
                    uiState = uiState,
                    onMarkAsRead = onMarkAsRead,
                    onMarkAllAsRead = onMarkAllAsRead,
                    onDeleteNotification = onDeleteNotification,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        NotificationSummaryCard(
            pushEnabled = settings.pushEnabled,
            summary = uiState.summary,
        )

        Spacer(modifier = Modifier.height(14.dp))
        DeleteAccountCard(
            onClick = onShowDeleteAccountDialog,
        )
    }

    if (uiState.isDeleteAccountDialogVisible) {
        DeleteAccountDialog(
            password = uiState.deleteAccountPassword,
            isLoading = uiState.isDeletingAccount,
            errorMessage = uiState.deleteAccountErrorMessage,
            onPasswordChange = onDeleteAccountPasswordChange,
            onDismiss = onDismissDeleteAccountDialog,
            onConfirm = onConfirmDeleteAccount,
        )
    }
}

@Composable
private fun RouteTrackingModeRow(
    mode: RouteTrackingMode,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "다녀온 길 기록",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = when (mode) {
                    RouteTrackingMode.Accurate -> "정확하게 기록 · 백그라운드 추적"
                    RouteTrackingMode.Simple -> "간단히 기록 · 앱 사용 중에만"
                    RouteTrackingMode.Off -> "기록 안 함"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = "변경",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF5F9CF6),
                checkedTrackColor = Color(0xFFAED0FF),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE4DDE1),
            ),
        )
    }
}

@Composable
private fun CaptureTimerSliderRow(
    enabled: Boolean,
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
) {
    val min: Int = AppPreferenceStore.MinCaptureTimerSeconds
    val max: Int = AppPreferenceStore.MaxCaptureTimerSeconds
    val activeAlpha: Float = if (enabled) 1f else 0.45f
    val safeSeconds: Int = seconds.coerceIn(min, max)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = activeAlpha),
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = "타이머 길이",
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = activeAlpha),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${safeSeconds}초",
                color = MaterialTheme.colorScheme.primary.copy(alpha = activeAlpha),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            )
        }
        Slider(
            enabled = enabled,
            value = safeSeconds.toFloat(),
            onValueChange = { value -> onSecondsChange(value.toInt().coerceIn(min, max)) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (max - min - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        Text(
            text = if (enabled) {
                "사진 인증 시 ${safeSeconds}초 카운트다운 후 촬영합니다."
            } else {
                "타이머 없이 누르는 즉시 촬영합니다."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NotificationListSection(
    uiState: SettingsUiState,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
    ) {
        SettingsDivider()
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "알림목록",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "읽지 않은 알림 ${uiState.summary.unreadCount}개",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = "모두 읽음",
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        enabled = uiState.summary.unreadCount > 0,
                        onClick = onMarkAllAsRead,
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                color = if (uiState.summary.unreadCount > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color(0xFFB9B1B6)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        when {
            uiState.isLoading -> NotificationStatusText(text = "알림을 불러오는 중")
            uiState.errorMessage != null -> NotificationStatusText(text = uiState.errorMessage)
            uiState.notifications.isEmpty() -> NotificationStatusText(text = "도착한 알림이 없어요.")
            else -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    uiState.notifications.take(NOTIFICATION_PREVIEW_LIMIT).forEach { notification ->
                        NotificationPreviewRow(
                            notification = notification,
                            onMarkAsRead = onMarkAsRead,
                            onDeleteNotification = onDeleteNotification,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationPreviewRow(
    notification: NotificationItem,
    onMarkAsRead: (String) -> Unit,
    onDeleteNotification: (String) -> Unit,
) {
    val rowBackground = if (notification.isRead) {
        Color.Transparent
    } else {
        Color(0xFFFFF7FA)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(rowBackground)
            .clickable { onMarkAsRead(notification.id) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(notification.type.toAccentColor().copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = notification.type.toIcon(),
                contentDescription = null,
                tint = notification.type.toAccentColor(),
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.type.toLabel(),
                    color = notification.type.toAccentColor(),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (!notification.isRead) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Text(
                text = notification.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notification.createdAt.toDisplayTime(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Rounded.DeleteForever,
            contentDescription = "알림 삭제",
            tint = Color(0xFFC49AAA),
            modifier = Modifier
                .size(24.dp)
                .clickable { onDeleteNotification(notification.id) }
                .padding(2.dp),
        )
    }
}

@Composable
private fun NotificationStatusText(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun NotificationSummaryCard(
    pushEnabled: Boolean,
    summary: NotificationSummary,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE5EF))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFB800),
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.toSummaryTitle(pushEnabled),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = summary.latestMessage ?: "새로운 문화 소식을 받을 준비가 되었어요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountCard(
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFE8EC)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = Color(0xFFE03B50),
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "회원탈퇴",
                    color = Color(0xFFE03B50),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text(
                    text = "계정과 활동 기록을 삭제합니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    password: String,
    isLoading: Boolean,
    errorMessage: String?,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "회원탈퇴")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "회원탈퇴를 진행하려면 비밀번호를 입력해 주세요.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "비밀번호") },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = "탈퇴")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading,
            ) {
                Text(text = "취소")
            }
        },
    )
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFD1BFC7)),
    )
}

private fun List<NotificationItem>.toSummary(): NotificationSummary =
    NotificationSummary(
        totalCount = size,
        unreadCount = count { notification -> !notification.isRead },
        latestMessage = firstOrNull()?.message,
    )

private fun NotificationSummary.toSummaryTitle(pushEnabled: Boolean): String =
    when {
        !pushEnabled -> "푸시알림이 꺼져 있어요."
        unreadCount > 0 -> "읽지 않은 알림 ${unreadCount}개가 기다리고 있어요."
        totalCount > 0 -> "모든 알림을 확인했어요."
        else -> "새로운 문화 소식을 받을 준비가 되었어요."
    }

private fun NotificationType.toLabel(): String =
    when (this) {
        NotificationType.MISSION -> "미션"
        NotificationType.SYSTEM -> "시스템"
        NotificationType.EVENT -> "이벤트"
        NotificationType.UNKNOWN -> "알림"
    }

private fun NotificationType.toIcon(): ImageVector =
    when (this) {
        NotificationType.MISSION -> Icons.Rounded.Notifications
        NotificationType.SYSTEM -> Icons.Rounded.Star
        NotificationType.EVENT -> Icons.Rounded.Event
        NotificationType.UNKNOWN -> Icons.Rounded.Notifications
    }

private fun NotificationType.toAccentColor(): Color =
    when (this) {
        NotificationType.MISSION -> Color(0xFFE94A7B)
        NotificationType.SYSTEM -> Color(0xFF5F9CF6)
        NotificationType.EVENT -> Color(0xFFFFA726)
        NotificationType.UNKNOWN -> Color(0xFF8F858B)
    }

private fun String.toDisplayTime(): String {
    if (length < NOTIFICATION_DATE_DISPLAY_LENGTH) {
        return this
    }
    return substring(5, NOTIFICATION_DATE_DISPLAY_LENGTH).replace("T", " ")
}

private const val NOTIFICATION_PREVIEW_LIMIT = 4
private const val NOTIFICATION_DATE_DISPLAY_LENGTH = 16
