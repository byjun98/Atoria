package com.ssafy.culture.ui.screen.quest

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.R
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.preferences.RouteTrackingMode
import com.ssafy.culture.data.route.RouteTracker
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.StoryChapterSnapshot
import com.ssafy.culture.domain.model.StoryMissionType
import com.ssafy.culture.domain.model.StoryProgress
import com.ssafy.culture.ui.screen.route.RouteTrackingModeDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestDetailUiState(
    val snapshot: StoryChapterSnapshot? = null,
    val isLoading: Boolean = true,
    val hasSubmittedCurrentChapter: Boolean = false,
    val isSubmittingMission: Boolean = false,
    val isMissionVisible: Boolean = false,
    val isRouteTrackingDialogVisible: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class QuestDetailViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
    private val routeTracker: RouteTracker,
    private val appPreferenceStore: AppPreferenceStore,
) : ViewModel() {
    private val _uiState: MutableStateFlow<QuestDetailUiState> = MutableStateFlow(QuestDetailUiState())
    val uiState: StateFlow<QuestDetailUiState> = _uiState.asStateFlow()

    init {
        loadChapter()
    }

    fun loadChapter() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isMissionVisible = false,
                    hasSubmittedCurrentChapter = false,
                    isSubmittingMission = false,
                    errorMessage = null,
                )
            }
            runCatching {
                storyRepository.getCurrentChapterSnapshot()
            }.onSuccess { snapshot ->
                if (snapshot != null) {
                    val hasChosen: Boolean = runCatching {
                        appPreferenceStore.hasChosenRouteTrackingMode.first()
                    }.getOrDefault(false)
                    if (hasChosen) {
                        routeTracker.start(snapshot.story.storyId)
                    } else {
                        _uiState.update { state -> state.copy(isRouteTrackingDialogVisible = true) }
                    }
                }
                _uiState.update { state ->
                    state.copy(
                        snapshot = snapshot,
                        isLoading = false,
                        errorMessage = if (snapshot == null) "진행할 챕터가 아직 없어요." else null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "챕터 정보를 불러오지 못했어요.",
                    )
                }
            }
        }
    }

    fun showMission() {
        _uiState.update { state ->
            state.copy(isMissionVisible = true)
        }
    }

    fun hideMission() {
        _uiState.update { state ->
            state.copy(isMissionVisible = false)
        }
    }

    fun selectRouteTrackingMode(mode: RouteTrackingMode) {
        viewModelScope.launch {
            runCatching { appPreferenceStore.setRouteTrackingMode(mode) }
            _uiState.update { state -> state.copy(isRouteTrackingDialogVisible = false) }
            val storyId: Long? = _uiState.value.snapshot?.story?.storyId
            if (storyId != null) {
                if (mode == RouteTrackingMode.Off) {
                    routeTracker.stop()
                } else {
                    routeTracker.start(storyId)
                }
            }
        }
    }

    fun dismissRouteTrackingDialog() {
        _uiState.update { state -> state.copy(isRouteTrackingDialogVisible = false) }
    }

    fun abandonStory(onAbandoned: () -> Unit) {
        val storyId: Long? = _uiState.value.snapshot?.story?.storyId
        if (storyId != null) {
            storyRepository.abandonStory(storyId)
        }
        onAbandoned()
    }

    fun submitMission(
        onAllSubmitted: (Long) -> Unit,
    ) {
        val snapshot: StoryChapterSnapshot = _uiState.value.snapshot ?: return
        if (_uiState.value.hasSubmittedCurrentChapter || _uiState.value.isSubmittingMission) return
        val storyId: Long = snapshot.story.storyId
        val chapterId: Long = snapshot.chapter.chapterId
        val remainingAfter: Int = snapshot.progress.chapters.count { chapter ->
            chapter.chapterId != chapterId && !chapter.isCompleted
        }
        _uiState.update { state ->
            state.copy(
                isSubmittingMission = true,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            runCatching {
                storyRepository.submitMission(
                    storyId = storyId,
                    chapterId = chapterId,
                )
            }.onSuccess {
                storyRepository.markChapterCompletedOptimistically(chapterId = chapterId)
                _uiState.update { state ->
                    state.copy(
                        hasSubmittedCurrentChapter = true,
                        isSubmittingMission = false,
                        errorMessage = null,
                    )
                }
                if (remainingAfter == 0) {
                    routeTracker.stop()
                    onAllSubmitted(storyId)
                } else {
                    loadChapter()
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isSubmittingMission = false,
                        errorMessage = throwable.localizedMessage ?: "Mission submit failed. Please try again.",
                    )
                }
            }
        }
    }
}

@Composable
fun QuestDetailRoute(
    onBack: () -> Unit,
    onOpenCamera: (Long, Long) -> Unit,
    onSubmit: (Long) -> Unit,
    viewModel: QuestDetailViewModel = hiltViewModel(),
) {
    val uiState: QuestDetailUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val handleBack = {
        if (uiState.isMissionVisible) {
            viewModel.hideMission()
        } else {
            onBack()
        }
    }
    BackHandler(enabled = uiState.isMissionVisible) {
        viewModel.hideMission()
    }
    QuestDetailScreen(
        uiState = uiState,
        onBack = handleBack,
        onOpenCamera = onOpenCamera,
        onShowMission = viewModel::showMission,
        onSubmit = { viewModel.submitMission(onAllSubmitted = onSubmit) },
        onGiveUp = { viewModel.abandonStory(onAbandoned = onBack) },
    )
    if (uiState.isRouteTrackingDialogVisible) {
        RouteTrackingModeDialog(
            onSelect = viewModel::selectRouteTrackingMode,
            onDismiss = viewModel::dismissRouteTrackingDialog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuestDetailScreen(
    uiState: QuestDetailUiState,
    onBack: () -> Unit,
    onOpenCamera: (Long, Long) -> Unit,
    onShowMission: () -> Unit,
    onSubmit: () -> Unit,
    onGiveUp: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (uiState.isMissionVisible) "미션" else "이야기",
                        style = MaterialTheme.typography.titleLarge,
                        color = QuestTextColor,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = QuestTextColor,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(questDetailBackgroundBrush()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, top = 12.dp, end = 24.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                when {
                    uiState.isLoading -> LoadingCard()
                    uiState.snapshot == null -> EmptyChapterCard()
                    else -> {
                        QuestProgress(
                            progress = uiState.snapshot.progress,
                            currentChapterId = uiState.snapshot.chapter.chapterId,
                        )
                        if (uiState.isMissionVisible) {
                            MissionCard(snapshot = uiState.snapshot)
                            MascotGuide(snapshot = uiState.snapshot)
                            QuestActions(
                                missionType = uiState.snapshot.chapter.mission.type,
                                hasSubmittedPhoto = uiState.snapshot.chapter.mission.progress.fileUrl != null ||
                                    uiState.snapshot.chapter.mission.progress.isCompleted,
                                hasSubmittedCurrentChapter = uiState.hasSubmittedCurrentChapter,
                                isSubmittingMission = uiState.isSubmittingMission,
                                onCameraClick = {
                                    onOpenCamera(
                                        uiState.snapshot.story.storyId,
                                        uiState.snapshot.chapter.chapterId,
                                    )
                                },
                                onSubmit = onSubmit,
                            )
                        } else {
                            StoryContentCard(
                                snapshot = uiState.snapshot,
                                onShowMission = onShowMission,
                                onGiveUp = onGiveUp,
                            )
                        }
                    }
                }
                if (uiState.errorMessage != null) {
                    QuestDetailStatusCard(text = uiState.errorMessage)
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = QuestPink,
            )
            Text(
                text = "이야기를 불러오는 중이에요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun EmptyChapterCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 5.dp,
    ) {
        Text(
            text = "진행할 이야기가 아직 없어요.",
            modifier = Modifier.padding(18.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun QuestProgress(
    progress: StoryProgress,
    currentChapterId: Long,
) {
    val currentIndex: Int = progress.chapters.indexOfFirst { chapter ->
        chapter.chapterId == currentChapterId
    }.takeIf { index -> index >= 0 } ?: 0
    val windowStartIndex: Int = (currentIndex - MaxProgressCenterOffset)
        .coerceIn(0, (progress.chapters.size - MaxProgressSteps).coerceAtLeast(0))
    val chapters = progress.chapters
        .drop(windowStartIndex)
        .take(MaxProgressSteps)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chapters.forEachIndexed { index, chapter ->
                ProgressStep(
                    label = "Quest ${windowStartIndex + index + 1}",
                    isActive = chapter.chapterId == currentChapterId,
                    isCompleted = chapter.isCompleted,
                )
                if (index < chapters.lastIndex) {
                    ProgressLine(isActive = chapter.isCompleted)
                }
            }
        }
    }
}

@Composable
private fun ProgressStep(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
) {
    val isHighlighted: Boolean = isActive || isCompleted
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = CircleShape,
            color = if (isHighlighted) QuestPink else Color.White,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (isHighlighted) QuestPink else Color(0xFFD9C6CE),
                        if (isHighlighted) QuestYellow else Color(0xFFD9C6CE),
                    ),
                ),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text(
                        text = label.takeLast(1),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isActive) Color.White else Color(0xFF8C7480),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) QuestTextColor else Color(0xFF8C7480),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RowScope.ProgressLine(isActive: Boolean) {
    Box(
        modifier = Modifier
            .weight(1f)
            .padding(horizontal = 8.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (isActive) QuestPink else Color(0xFFEBDDE3)),
    )
}

@Composable
private fun StoryContentCard(
    snapshot: StoryChapterSnapshot,
    onShowMission: () -> Unit,
    onGiveUp: () -> Unit,
) {
    val chapter = snapshot.chapter
    val placeTitle: String = chapter.place.title.ifBlank { "현재 장소" }
    val storyText: String = chapter.storyContent.content
        ?: snapshot.story.intro
        ?: "이 장소에서 새로운 이야기가 시작돼요."
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.94f),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Chapter ${chapter.sequence}",
                style = MaterialTheme.typography.labelLarge,
                color = QuestPink,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = placeTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFF6DF),
            ) {
                Text(
                    text = storyText,
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF6C2C3D),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onShowMission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = QuestPink,
                        contentColor = Color.White,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "미션 도전하기",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(
                    onClick = onGiveUp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF8C7480),
                    ),
                    border = BorderStroke(
                        width = 1.dp,
                        color = Color(0xFFD9C6CE),
                    ),
                ) {
                    Text(
                        text = "포기하기",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MissionCard(
    snapshot: StoryChapterSnapshot,
) {
    val chapter = snapshot.chapter
    val missionTitle: String = chapter.mission.title.ifBlank { "오늘의 미션" }
    val placeTitle: String = chapter.place.title.ifBlank { "현재 장소" }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "Quest ${chapter.sequence}",
                style = MaterialTheme.typography.labelLarge,
                color = QuestPink,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = missionTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = QuestTextColor,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Place,
                    contentDescription = null,
                    tint = QuestPink,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = placeTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF7A5565),
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFFF6DF),
            ) {
                Text(
                    text = chapter.mission.description,
                    modifier = Modifier.padding(18.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF6C2C3D),
                )
            }
        }
    }
}

@Composable
private fun MascotGuide(
    snapshot: StoryChapterSnapshot,
) {
    val mission = snapshot.chapter.mission
    val guideText: String = mission.verificationHint.ifBlank {
        mission.description
    }.ifBlank {
        "사진을 찍으면 다음 이야기로 이동할 준비가 끝나요."
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 18.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MagicWandHintIcon()
                    Text(
                        text = "아토리아의 힌트",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    modifier = Modifier.height(0.dp),
                    text = "아토리아의 힌트",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Transparent,
                )
                Text(
                    text = guideText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF7A5565),
                )
            }
            Image(
                painter = painterResource(id = R.drawable.atoria_mascot),
                contentDescription = "아토리아",
                modifier = Modifier.size(0.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun MagicWandHintIcon(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(34.dp),
        shape = CircleShape,
        color = Color(0xFFFFF2BF),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val wandStart = Offset(size.width * 0.32f, size.height * 0.72f)
            val wandEnd = Offset(size.width * 0.68f, size.height * 0.34f)
            drawLine(
                color = Color(0xFF7A314A),
                start = wandStart,
                end = wandEnd,
                strokeWidth = 4.5f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f),
                start = Offset(size.width * 0.48f, size.height * 0.54f),
                end = Offset(size.width * 0.62f, size.height * 0.40f),
                strokeWidth = 1.6f,
                cap = StrokeCap.Round,
            )
            val largeSparkle = Offset(size.width * 0.72f, size.height * 0.24f)
            drawLine(
                color = QuestPink,
                start = Offset(largeSparkle.x - 6.5f, largeSparkle.y),
                end = Offset(largeSparkle.x + 6.5f, largeSparkle.y),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = QuestPink,
                start = Offset(largeSparkle.x, largeSparkle.y - 6.5f),
                end = Offset(largeSparkle.x, largeSparkle.y + 6.5f),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
            val smallSparkle = Offset(size.width * 0.28f, size.height * 0.30f)
            drawLine(
                color = QuestYellow,
                start = Offset(smallSparkle.x - 4.3f, smallSparkle.y),
                end = Offset(smallSparkle.x + 4.3f, smallSparkle.y),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = QuestYellow,
                start = Offset(smallSparkle.x, smallSparkle.y - 4.3f),
                end = Offset(smallSparkle.x, smallSparkle.y + 4.3f),
                strokeWidth = 1.8f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = QuestPink,
                radius = 2.1f,
                center = Offset(size.width * 0.76f, size.height * 0.58f),
            )
        }
    }
}

@Composable
private fun QuestActions(
    missionType: StoryMissionType,
    hasSubmittedPhoto: Boolean,
    hasSubmittedCurrentChapter: Boolean,
    isSubmittingMission: Boolean,
    onCameraClick: () -> Unit,
    onSubmit: () -> Unit,
) {
    val canSubmit: Boolean = when (missionType) {
        StoryMissionType.Photo,
        StoryMissionType.Unknown -> hasSubmittedPhoto
        StoryMissionType.Choice,
        StoryMissionType.Quiz,
        StoryMissionType.Action,
        StoryMissionType.Text -> true
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onCameraClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = QuestPink,
            ),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(QuestPink, QuestYellow),
                ),
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "카메라로 인증하기",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Button(
            onClick = onSubmit,
            enabled = canSubmit && !hasSubmittedCurrentChapter && !isSubmittingMission,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = QuestYellow,
                contentColor = Color(0xFF6C2C3D),
                disabledContainerColor = Color(0xFFFFE8A2),
                disabledContentColor = Color(0xFF8C7480),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
        ) {
            Text(
                text = "제출하기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!canSubmit) {
            QuestDetailStatusCard(text = "사진 인증을 통과해야 다음 퀘스트로 이동할 수 있어요.")
        }
    }
}

@Composable
private fun QuestDetailStatusCard(
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun questDetailBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.background,
        ),
    )
private val QuestPink = Color(0xFFE94A7B)
private val QuestYellow = Color(0xFFFFCF54)
private val QuestTextColor = Color(0xFF35121F)
private const val MaxProgressSteps = 5
private const val MaxProgressCenterOffset = 2
