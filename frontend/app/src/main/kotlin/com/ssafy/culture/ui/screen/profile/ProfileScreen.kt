package com.ssafy.culture.ui.screen.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.auth.AuthRepository
import com.ssafy.culture.data.user.UserRepository
import com.ssafy.culture.domain.model.UserProfile
import com.ssafy.culture.ui.component.CultureAsyncImage
import com.ssafy.culture.ui.component.DecorativeCloud
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.component.StatusPill
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val ProfileCardShape = RoundedCornerShape(34.dp)
private const val ArtifactColumnCount: Int = 2
private const val UnitySaveFileName: String = "rebuild_keeponmining.json"

data class ProfileUiState(
    val user: UserProfile? = null,
    val artifacts: List<ProfileArtifactUi> = buildProfileArtifacts(),
    val isLoading: Boolean = true,
    val isArtifactLoading: Boolean = true,
    val isLoggingOut: Boolean = false,
    val errorMessage: String? = null,
)

data class ProfileArtifactUi(
    val title: String,
    val assetPath: String,
    val isCollected: Boolean,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        observeCurrentUser()
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = state.user == null,
                    isArtifactLoading = true,
                    errorMessage = null,
                )
            }
            runCatching {
                userRepository.refreshCurrentUser()
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "사용자 정보를 불러오지 못했어요.",
                    )
                }
            }
            refreshArtifactsInternal()
        }
    }

    fun refreshArtifacts() {
        viewModelScope.launch {
            _uiState.update { state -> state.copy(isArtifactLoading = true) }
            refreshArtifactsInternal()
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            userRepository.observeCurrentUser().collect { user ->
                _uiState.update { state ->
                    state.copy(
                        user = user,
                        isLoading = false,
                    )
                }
            }
        }
    }

    private suspend fun refreshArtifactsInternal() {
        runCatching {
            loadCollectedArtifactIds(context)
        }.onSuccess { collectedIds ->
            _uiState.update { state ->
                state.copy(
                    artifacts = buildProfileArtifacts(collectedIds),
                    isArtifactLoading = false,
                )
            }
        }.onFailure {
            _uiState.update { state ->
                state.copy(
                    isArtifactLoading = false,
                    errorMessage = state.errorMessage ?: "유물 목록을 업데이트하지 못했어요.",
                )
            }
        }
    }

    fun logout(
        onLoggedOut: () -> Unit,
    ) {
        if (_uiState.value.isLoggingOut) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoggingOut = true, errorMessage = null)
            }
            runCatching {
                authRepository.logoutCurrentSession()
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(isLoggingOut = false)
                }
                onLoggedOut()
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoggingOut = false,
                        errorMessage = throwable.localizedMessage ?: "로그아웃에 실패했어요.",
                    )
                }
            }
        }
    }
}

private data class ProfileArtifactDefinition(
    val gameId: Int,
    val title: String,
    val fileName: String,
)

private val ArtifactDefinitions = listOf(
    ProfileArtifactDefinition(gameId = 0, title = "경주 고선사지 삼층석탑", fileName = "경주 고선사지 삼층석탑.png"),
    ProfileArtifactDefinition(gameId = 1, title = "경주 봉황대", fileName = "경주 봉황대.png"),
    ProfileArtifactDefinition(gameId = 2, title = "경주 석빙고", fileName = "경주 석빙고.png"),
    ProfileArtifactDefinition(gameId = 3, title = "경주 향교", fileName = "경주 향교.png"),
    ProfileArtifactDefinition(gameId = 4, title = "기타 고분", fileName = "기타 고분.png"),
    ProfileArtifactDefinition(gameId = 5, title = "동궁과 월지", fileName = "동궁과 월지.png"),
    ProfileArtifactDefinition(gameId = 6, title = "불국사 다보탑", fileName = "불국사 다보탑.png"),
    ProfileArtifactDefinition(gameId = 7, title = "불국사 대웅전", fileName = "불국사 대웅전.png"),
    ProfileArtifactDefinition(gameId = 8, title = "불국사 백운교", fileName = "불국사 백운교.png"),
    ProfileArtifactDefinition(gameId = 9, title = "불국사 석가탑", fileName = "불국사 석가탑.png"),
    ProfileArtifactDefinition(gameId = 10, title = "에밀레종(성덕대왕신종)", fileName = "에밀레종(성덕대왕신종).png"),
    ProfileArtifactDefinition(gameId = 11, title = "천마총", fileName = "천마총.png"),
    ProfileArtifactDefinition(gameId = 12, title = "천마총 금관", fileName = "천마총 금관.png"),
    ProfileArtifactDefinition(gameId = 13, title = "첨성대", fileName = "첨성대.png"),
)

private fun buildProfileArtifacts(
    collectedIds: Set<Int> = emptySet(),
): List<ProfileArtifactUi> = ArtifactDefinitions.map { definition ->
    ProfileArtifactUi(
        title = definition.title,
        assetPath = "file:///android_asset/artifacts/${Uri.encode(definition.fileName)}",
        isCollected = definition.gameId in collectedIds,
    )
}

private suspend fun loadCollectedArtifactIds(context: Context): Set<Int> = withContext(Dispatchers.IO) {
    val saveFile = findUnitySaveFile(context) ?: return@withContext emptySet()
    val artifactsFound = JSONObject(saveFile.readText()).optJSONArray("artifactsFound")
        ?: return@withContext emptySet()
    ArtifactDefinitions.map { definition -> definition.gameId }
        .filter { gameId -> gameId < artifactsFound.length() && artifactsFound.optBoolean(gameId, false) }
        .toSet()
}

private fun findUnitySaveFile(context: Context): File? {
    val candidates = buildList {
        context.getExternalFilesDir(null)?.let { directory ->
            add(File(directory, UnitySaveFileName))
        }
        add(File(context.filesDir, UnitySaveFileName))
        add(File(context.noBackupFilesDir, UnitySaveFileName))
    }
    return candidates.firstOrNull { file -> file.isFile }
}

@Composable
fun ProfileRoute(
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onLogout: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshArtifacts()
    }
    ProfileScreen(
        uiState = uiState,
        onOpenHome = onOpenHome,
        onOpenMap = onOpenMap,
        onOpenStory = onOpenStory,
        onLogout = {
            viewModel.logout(onLoggedOut = onLogout)
        },
        onOpenEditProfile = onOpenEditProfile,
        onOpenSettings = onOpenSettings,
        onOpenGame = {
            context.startActivity(
                Intent(context, com.unity3d.player.UnityPlayerGameActivity::class.java),
            )
        },
    )
}

@Composable
private fun ProfileScreen(
    uiState: ProfileUiState,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onLogout: () -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGame: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                selectedDestination = MainDestination.Profile,
                onHomeClick = onOpenHome,
                onMapClick = onOpenMap,
                onStoryClick = onOpenStory,
                onProfileClick = {},
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(profileBackgroundBrush()),
        ) {
            ProfileSkyBackground()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp)
                    .padding(top = 34.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileTitleBar(
                    isLoggingOut = uiState.isLoggingOut,
                    onLogout = onLogout,
                )
                Spacer(modifier = Modifier.height(22.dp))
                ProfileHeroCard(
                    user = uiState.user,
                    isLoading = uiState.isLoading,
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (uiState.errorMessage != null) {
                    StatusPill(text = uiState.errorMessage, bold = true)
                    Spacer(modifier = Modifier.height(14.dp))
                }
                ProfileMenu(
                    onOpenEditProfile = onOpenEditProfile,
                    onOpenSettings = onOpenSettings,
                    onOpenGame = onOpenGame,
                )
                Spacer(modifier = Modifier.height(14.dp))
                ArtifactCollectionSection(
                    artifacts = uiState.artifacts,
                    isLoading = uiState.isArtifactLoading,
                )
            }
        }
    }
}

@Composable
private fun ProfileSkyBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
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
        DecorativeCloud(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 16.dp, y = 88.dp)
                .size(width = 154.dp, height = 84.dp),
            colors = listOf(Color(0xFFFFD8E8), Color.White),
        )
        DecorativeCloud(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-18).dp, y = 122.dp)
                .size(width = 168.dp, height = 92.dp),
            colors = listOf(Color(0xFFFFE4F0), Color.White),
        )
    }
}

@Composable
private fun profileBackgroundBrush(): Brush =
    Brush.verticalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.background,
        ),
    )

@Composable
private fun ProfileHeroCard(
    user: UserProfile?,
    isLoading: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 18.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFFF7BA8), Color(0xFFFFC2D5)),
                        ),
                    )
                    .border(5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(66.dp),
                )
                Sparkle(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = 2.dp), 24.dp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            if (isLoading && user == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = user?.nickname?.takeIf(String::isNotBlank) ?: "문화 탐험가",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!user?.email.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = user?.email.orEmpty(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTitleBar(
    isLoggingOut: Boolean,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .tossClickable(
                    pressedScale = CultureMotion.SubtlePressedScale,
                    onClick = {
                        if (!isLoggingOut) {
                            onLogout()
                        }
                    },
                ),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isLoggingOut) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = if (isLoggingOut) "로그아웃 중" else "로그아웃",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        StickerTitle(text = "프로필")
    }
}

@Composable
private fun ProfileMenu(
    onOpenEditProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGame: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            ProfileMenuItem(
                icon = Icons.Rounded.Edit,
                title = "내 정보 수정",
                subtitle = "닉네임과 프로필 꾸미기",
                onClick = onOpenEditProfile,
            )
            ProfileMenuItem(
                icon = Icons.Rounded.SportsEsports,
                title = "게임 시작",
                subtitle = "유니티 미니게임 열기",
                onClick = onOpenGame,
            )
            ProfileMenuItem(
                icon = Icons.Rounded.Settings,
                title = "설정",
                subtitle = "알림과 앱 환경 관리",
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun ArtifactCollectionSection(
    artifacts: List<ProfileArtifactUi>,
    isLoading: Boolean,
) {
    val collectedCount = artifacts.count { artifact -> artifact.isCollected }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ProfileCardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "수집한 유물",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "$collectedCount/${artifacts.size}",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                artifacts.chunked(ArtifactColumnCount).forEach { rowArtifacts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowArtifacts.forEach { artifact ->
                            ArtifactItem(
                                artifact = artifact,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(ArtifactColumnCount - rowArtifacts.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactItem(
    artifact: ProfileArtifactUi,
    modifier: Modifier = Modifier,
) {
    val itemBackground = if (artifact.isCollected) {
        Brush.linearGradient(listOf(Color(0xFFFFFAE8), Color.White))
    } else {
        Brush.linearGradient(listOf(Color(0xFFFFF3F7), Color(0xFFFFE4EE)))
    }
    val borderColor = if (artifact.isCollected) Color(0xFFFFD56A) else Color(0xFFFFC6D8)
    val textColor = if (artifact.isCollected) MaterialTheme.colorScheme.onSurface else Color(0xFFAA7B90)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(itemBackground)
            .border(2.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .border(3.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (artifact.isCollected) {
                CultureAsyncImage(
                    model = artifact.assetPath,
                    contentDescription = artifact.title,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                    placeholder = null,
                    error = null,
                )
            } else {
                Text(
                    text = "?",
                    color = Color(0xFFB97C96),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                )
            }
        }
        Text(
            text = if (artifact.isCollected) artifact.title else "?",
            color = textColor,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tossClickable(
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFFFE5EF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StickerTitle(text: String) {
    val titleStyle = MaterialTheme.typography.displayLarge.copy(
        fontSize = 56.sp,
        lineHeight = 62.sp,
        fontWeight = FontWeight.ExtraBold,
        shadow = Shadow(
            color = Color(0x55FFFFFF),
            blurRadius = 10f,
            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
        ),
    )
    Box {
        listOf(
            (-2).dp to 0.dp,
            2.dp to 0.dp,
            0.dp to (-2).dp,
            0.dp to 2.dp,
            2.dp to 2.dp,
            (-2).dp to 2.dp,
            2.dp to (-2).dp,
            (-2).dp to (-2).dp,
        ).forEach { offset ->
            Text(
                text = text,
                modifier = Modifier.offset(offset.first, offset.second),
                style = titleStyle,
                color = Color(0xFF91556E),
            )
        }
        Text(
            text = text,
            modifier = Modifier.offset(y = 3.dp),
            style = titleStyle,
            color = Color(0xFFFFDCE7).copy(alpha = 0.9f),
        )
        Text(text = text, style = titleStyle, color = Color(0xFFFFF6FA))
    }
}

@Composable
private fun Sparkle(
    modifier: Modifier,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Rounded.Star,
        contentDescription = null,
        tint = Color(0xFFFFD85C),
        modifier = modifier.size(size),
    )
}
