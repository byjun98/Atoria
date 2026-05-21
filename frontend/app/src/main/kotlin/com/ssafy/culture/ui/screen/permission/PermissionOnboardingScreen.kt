package com.ssafy.culture.ui.screen.permission

import android.Manifest
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.ui.layout.CultureHeightClass
import com.ssafy.culture.ui.layout.cultureHeightClass
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PermissionOnboardingUiState(
    val isChecking: Boolean = true,
    val isCompleted: Boolean = false,
)

private data class PermissionLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val topSpacer: Dp,
    val itemSpacing: Dp,
    val cardPadding: Dp,
    val iconSize: Dp,
    val iconImageSize: Dp,
    val actionTopSpacer: Dp,
    val primaryButtonHeight: Dp,
    val secondaryButtonHeight: Dp,
)

@HiltViewModel
class PermissionOnboardingViewModel @Inject constructor(
    private val appPreferenceStore: AppPreferenceStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionOnboardingUiState())
    val uiState: StateFlow<PermissionOnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val isCompleted: Boolean = appPreferenceStore.permissionOnboardingCompleted.first()
            _uiState.update { state ->
                state.copy(
                    isChecking = false,
                    isCompleted = isCompleted,
                )
            }
        }
    }

    fun complete() {
        viewModelScope.launch {
            appPreferenceStore.setPermissionOnboardingCompleted(true)
            _uiState.update { state ->
                state.copy(isCompleted = true)
            }
        }
    }
}

@Composable
fun PermissionOnboardingRoute(
    onComplete: () -> Unit,
    viewModel: PermissionOnboardingViewModel = hiltViewModel(),
) {
    val uiState: PermissionOnboardingUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.complete()
    }
    LaunchedEffect(uiState.isChecking, uiState.isCompleted) {
        if (!uiState.isChecking && uiState.isCompleted) {
            onComplete()
        }
    }
    PermissionOnboardingScreen(
        uiState = uiState,
        onAllowClick = {
            val permissions: Array<String> = buildRuntimePermissions()
            if (permissions.isEmpty()) {
                viewModel.complete()
            } else {
                permissionLauncher.launch(permissions)
            }
        },
        onLaterClick = viewModel::complete,
    )
}

@Composable
private fun PermissionOnboardingScreen(
    uiState: PermissionOnboardingUiState,
    onAllowClick: () -> Unit,
    onLaterClick: () -> Unit,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFD6E1),
                        Color(0xFFFFF0D1),
                        Color(0xFFFFFAF2),
                    ),
                ),
            ),
    ) {
        val metrics = permissionLayoutMetrics(
            maxHeight = maxHeight,
            isLandscape = isLandscape,
        )

        if (uiState.isChecking) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary,
            )
            return@BoxWithConstraints
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.verticalPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(metrics.itemSpacing),
        ) {
            Spacer(modifier = Modifier.height(metrics.topSpacer))
            Text(
                text = "여행 기록을 위해 권한이 필요해요",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "처음 한 번만 안내하고, 거부한 권한은 기능을 사용할 때 다시 요청할게요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionInfoCard(
                icon = Icons.Rounded.PhotoCamera,
                title = "카메라",
                description = "퀘스트 인증 사진을 앱 안에서 바로 촬영해요.",
                isRequired = true,
                metrics = metrics,
            )
            PermissionInfoCard(
                icon = Icons.Rounded.LocationOn,
                title = "위치",
                description = "지도에서 현재 위치와 가까운 문화 장소를 확인해요.",
                isRequired = true,
                metrics = metrics,
            )
            PermissionInfoCard(
                icon = Icons.Rounded.Notifications,
                title = "알림",
                description = "결과물이 완성되었을 때 알려드릴 수 있어요.",
                isRequired = false,
                metrics = metrics,
            )
            PermissionInfoCard(
                icon = Icons.Rounded.Image,
                title = "갤러리",
                description = "전체 사진 권한 없이 선택한 사진만 가져와요.",
                isRequired = false,
                metrics = metrics,
            )
            Spacer(modifier = Modifier.height(metrics.actionTopSpacer))
            Button(
                onClick = onAllowClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.primaryButtonHeight),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ),
            ) {
                Text(
                    text = "허용하고 시작하기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                onClick = onLaterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.secondaryButtonHeight),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainer),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(text = "나중에 하기")
            }
        }
    }
}

private fun permissionLayoutMetrics(
    maxHeight: Dp,
    isLandscape: Boolean,
): PermissionLayoutMetrics {
    if (isLandscape) {
        return PermissionLayoutMetrics(
            horizontalPadding = 20.dp,
            verticalPadding = 18.dp,
            topSpacer = 16.dp,
            itemSpacing = 12.dp,
            cardPadding = 16.dp,
            iconSize = 44.dp,
            iconImageSize = 24.dp,
            actionTopSpacer = 8.dp,
            primaryButtonHeight = 54.dp,
            secondaryButtonHeight = 52.dp,
        )
    }
    return when (cultureHeightClass(maxHeight)) {
        CultureHeightClass.Compact -> PermissionLayoutMetrics(
            horizontalPadding = 22.dp,
            verticalPadding = 20.dp,
            topSpacer = 44.dp,
            itemSpacing = 12.dp,
            cardPadding = 14.dp,
            iconSize = 42.dp,
            iconImageSize = 23.dp,
            actionTopSpacer = 4.dp,
            primaryButtonHeight = 52.dp,
            secondaryButtonHeight = 50.dp,
        )
        CultureHeightClass.Medium -> PermissionLayoutMetrics(
            horizontalPadding = 24.dp,
            verticalPadding = 24.dp,
            topSpacer = 80.dp,
            itemSpacing = 14.dp,
            cardPadding = 16.dp,
            iconSize = 46.dp,
            iconImageSize = 25.dp,
            actionTopSpacer = 8.dp,
            primaryButtonHeight = 54.dp,
            secondaryButtonHeight = 52.dp,
        )
        CultureHeightClass.Expanded -> PermissionLayoutMetrics(
            horizontalPadding = 24.dp,
            verticalPadding = 28.dp,
            topSpacer = 120.dp,
            itemSpacing = 18.dp,
            cardPadding = 18.dp,
            iconSize = 48.dp,
            iconImageSize = 26.dp,
            actionTopSpacer = 12.dp,
            primaryButtonHeight = 56.dp,
            secondaryButtonHeight = 54.dp,
        )
    }
}

@Composable
private fun PermissionInfoCard(
    icon: ImageVector,
    title: String,
    description: String,
    isRequired: Boolean,
    metrics: PermissionLayoutMetrics,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(metrics.cardPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(metrics.iconSize),
                shape = CircleShape,
                color = if (isRequired) MaterialTheme.colorScheme.surfaceContainer else PermissionSoftYellow,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isRequired) MaterialTheme.colorScheme.primary else PermissionOrange,
                        modifier = Modifier.size(metrics.iconImageSize),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "$title ${if (isRequired) "필수" else "선택"}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildRuntimePermissions(): Array<String> =
    buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

private val PermissionSoftYellow = Color(0xFFFFF2C7)
private val PermissionOrange = Color(0xFFE8922E)
