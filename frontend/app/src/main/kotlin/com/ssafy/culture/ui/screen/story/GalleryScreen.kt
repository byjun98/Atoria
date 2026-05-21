package com.ssafy.culture.ui.screen.story

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.data.ebook.EbookRepository
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.EbookResult
import com.ssafy.culture.domain.model.EbookStatus
import com.ssafy.culture.domain.model.StoryProgressChapter
import com.ssafy.culture.domain.model.StoryQuestSnapshot
import com.ssafy.culture.domain.model.StoryStatus
import com.ssafy.culture.ui.component.EmptyState
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val results: List<GalleryResult> = emptyList(),
    val currentStory: StoryQuestSnapshot? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val ebookRepository: EbookRepository,
    private val storyRepository: StoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        loadGallery()
    }

    fun loadGallery(): Unit {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val results = ebookRepository.getEbooks().mapIndexed { index, ebook ->
                    ebook.toGalleryResult(index)
                }
                val currentStory = storyRepository.getCurrentQuestSnapshot()
                    ?.takeIf(StoryQuestSnapshot::isInProgress)
                GalleryLoadResult(
                    results = results,
                    currentStory = currentStory,
                )
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        results = result.results,
                        currentStory = result.currentStory,
                        isLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "결과물을 불러오지 못했어요.",
                    )
                }
            }
        }
    }
}

private data class GalleryLoadResult(
    val results: List<GalleryResult>,
    val currentStory: StoryQuestSnapshot?,
)

@Composable
fun GalleryRoute(
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCurrentStory: () -> Unit,
    onOpenResultDetail: (String) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel(),
): Unit {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadGallery()
    }
    GalleryScreen(
        uiState = uiState,
        onOpenHome = onOpenHome,
        onOpenMap = onOpenMap,
        onOpenProfile = onOpenProfile,
        onOpenCurrentStory = onOpenCurrentStory,
        onOpenResultDetail = onOpenResultDetail,
    )
}

@Composable
fun GalleryScreen(
    uiState: GalleryUiState,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenCurrentStory: () -> Unit,
    onOpenResultDetail: (String) -> Unit,
): Unit {
    val results: List<GalleryResult> = uiState.results
    val currentStory: StoryQuestSnapshot? = uiState.currentStory
    var selectedFilter by remember { mutableStateOf(GalleryFilter.All) }
    val filteredResults: List<GalleryResult> = remember(results, selectedFilter) {
        results.filterBy(selectedFilter)
    }
    val showCurrentStory: Boolean = selectedFilter != GalleryFilter.Completed && currentStory != null
    val showRecentResults: Boolean = selectedFilter != GalleryFilter.InProgress
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                selectedDestination = MainDestination.Story,
                onHomeClick = onOpenHome,
                onMapClick = onOpenMap,
                onStoryClick = {},
                onProfileClick = onOpenProfile,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GalleryBackgroundBrush),
        ) {
            GalleryBackdrop()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 26.dp,
                    end = 24.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    GalleryHeader()
                }
                item {
                    GallerySummaryPanel(
                        results = results,
                        currentStory = currentStory,
                        selectedFilter = selectedFilter,
                        onFilterSelected = { filter -> selectedFilter = filter },
                    )
                }
                if (showCurrentStory) {
                    item {
                        CurrentStoryPanel(
                            snapshot = currentStory,
                            onOpenCurrentStory = onOpenCurrentStory,
                        )
                    }
                }
                if (selectedFilter == GalleryFilter.InProgress && currentStory == null && !uiState.isLoading) {
                    item {
                        GalleryStatusPill(text = "진행 중인 스토리가 없어요.")
                    }
                }
                if (showRecentResults) {
                    item {
                        SectionTitle()
                    }
                    items(
                        items = filteredResults,
                        key = GalleryResult::id,
                    ) { result ->
                        GalleryResultCard(
                            result = result,
                            onClick = { onOpenResultDetail(result.ebookId) },
                        )
                    }
                }
                if (uiState.isLoading && filteredResults.isEmpty()) {
                    item {
                        GalleryStatusPill(text = "결과물을 불러오는 중이에요.")
                    }
                }
                if (showRecentResults && !uiState.isLoading && uiState.errorMessage == null && results.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.AutoMirrored.Rounded.MenuBook,
                            title = "아직 완료된 이야기가 없어요",
                            description = "첫 문화 여행 기록을 만들고 우리만의 이야기를 모아보세요.",
                            primaryActionLabel = "첫 이야기 만들기",
                            onPrimaryAction = onOpenHome,
                        )
                    }
                }
                if (showRecentResults && !uiState.isLoading && uiState.errorMessage == null && results.isNotEmpty() && filteredResults.isEmpty()) {
                    item {
                        GalleryStatusPill(text = "선택한 상태의 스토리가 없어요.")
                    }
                }
                if (uiState.errorMessage != null) {
                    item {
                        GalleryStatusPill(text = uiState.errorMessage)
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryBackdrop(): Unit {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(18) { index ->
            val x: Float = ((index * 67) % 100) / 100f * size.width
            val y: Float = ((index * 41) % 100) / 100f * size.height
            val length: Float = 5f + (index % 3) * 3f
            drawLine(
                color = Color.White.copy(alpha = 0.56f),
                start = Offset(x - length, y),
                end = Offset(x + length, y),
                strokeWidth = 2.1f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.56f),
                start = Offset(x, y - length),
                end = Offset(x, y + length),
                strokeWidth = 2.1f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun GalleryHeader(): Unit {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = CircleShape,
            color = GalleryYellow,
            shadowElevation = 8.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = GalleryDeepPlum,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Story",
                style = MaterialTheme.typography.labelLarge,
                color = GalleryPink,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "나의 이야기 갤러리",
                style = MaterialTheme.typography.headlineMedium,
                color = GalleryDeepPlum,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "문화 여정에서 만든 결과를 모아봤어요.",
                style = MaterialTheme.typography.bodySmall,
                color = GalleryMutedPlum,
            )
        }
    }
}

@Composable
private fun GallerySummaryPanel(
    results: List<GalleryResult>,
    currentStory: StoryQuestSnapshot?,
    selectedFilter: GalleryFilter,
    onFilterSelected: (GalleryFilter) -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val completedCount: Int = results.count { result -> result.status == GalleryStatus.Completed }
    val inProgressCount: Int = if (currentStory == null) 0 else 1
    val totalCount: Int = results.size + inProgressCount
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GallerySoftPink),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "이번 주 스토리",
                        style = MaterialTheme.typography.titleLarge,
                        color = GalleryDeepPlum,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "완성된 추억과 생성 중인 기록을 한눈에 확인해요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GalleryMutedPlum,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = GallerySoftYellow,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = GalleryPink,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SummaryChip(
                    label = "전체",
                    value = "$totalCount",
                    isSelected = selectedFilter == GalleryFilter.All,
                    onClick = { onFilterSelected(GalleryFilter.All) },
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    label = "완료",
                    value = "$completedCount",
                    isSelected = selectedFilter == GalleryFilter.Completed,
                    onClick = { onFilterSelected(GalleryFilter.Completed) },
                    modifier = Modifier.weight(1f),
                )
                SummaryChip(
                    label = "진행 중",
                    value = "$inProgressCount",
                    isSelected = selectedFilter == GalleryFilter.InProgress,
                    onClick = { onFilterSelected(GalleryFilter.InProgress) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CurrentStoryPanel(
    snapshot: StoryQuestSnapshot,
    onOpenCurrentStory: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val totalCount: Int = snapshot.progress.totalCount
    val completedCount: Int = snapshot.progress.completedCount
    val progressRate: Float = snapshot.progress.progressRate.coerceIn(0f, 1f)
    val courseText: String = snapshot.progress.chapters
        .map(StoryProgressChapter::placeTitle)
        .filter(String::isNotBlank)
        .distinct()
        .joinToString(separator = " - ")
        .ifBlank { snapshot.story.title.ifBlank { "코스를 준비 중이에요." } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GallerySoftPink),
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    modifier = Modifier.size(58.dp),
                    shape = CircleShape,
                    color = GallerySoftPink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = GalleryPink,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "오늘의 코스",
                        style = MaterialTheme.typography.titleLarge,
                        color = GalleryDeepPlum,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = courseText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = GalleryMutedPlum,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "진행률",
                        style = MaterialTheme.typography.bodySmall,
                        color = GalleryMutedPlum,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "$completedCount / $totalCount 완료",
                        style = MaterialTheme.typography.bodySmall,
                        color = GalleryPink,
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinearProgressIndicator(
                    progress = { progressRate },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(9.dp),
                    color = GalleryPink,
                    trackColor = GallerySoftPink,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StoryInfoPill(
                    text = "${snapshot.progress.toPlaceCount()}곳",
                    modifier = Modifier.weight(1f),
                )
                StoryInfoPill(
                    text = "${completedCount}개 완료",
                    modifier = Modifier.weight(1f),
                )
                StoryInfoPill(
                    text = snapshot.currentChapter?.placeTitle ?: "대기 중",
                    modifier = Modifier.weight(1f),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            }
            Button(
                onClick = onOpenCurrentStory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GalleryPink,
                    contentColor = Color.White,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "코스 시작/이어하기",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StoryInfoPill(
    text: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = GallerySoftPink,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            color = GalleryDeepPlum,
            fontWeight = FontWeight.Bold,
            maxLines = maxLines,
            overflow = overflow,
        )
    }
}

@Composable
private fun CurrentStoryRoutePreview(
    chapters: List<StoryProgressChapter>,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFFFFFBFD),
        border = BorderStroke(1.dp, GallerySoftPink),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = GallerySoftPink,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Map,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = GalleryPink,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "미션 수행 경로",
                        style = MaterialTheme.typography.titleMedium,
                        color = GalleryDeepPlum,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "완료는 체크, 현재는 진행 중, 예정지는 대기로 보여줘요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GalleryMutedPlum,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteLegendPill(
                    label = "완료",
                    containerColor = Color(0xFFDDF8EA),
                    contentColor = Color(0xFF159866),
                    modifier = Modifier.weight(1f),
                )
                RouteLegendPill(
                    label = "진행 중",
                    containerColor = Color(0xFFFFF0C6),
                    contentColor = Color(0xFF9A6500),
                    modifier = Modifier.weight(1f),
                )
                RouteLegendPill(
                    label = "예정",
                    containerColor = Color(0xFFF2EAF0),
                    contentColor = GalleryMutedPlum,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun RouteLegendPill(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    value: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.tossClickable(
            role = Role.Button,
            pressedScale = CultureMotion.SubtlePressedScale,
            onClick = onClick,
        ),
        shape = RoundedCornerShape(18.dp),
        color = if (isSelected) GalleryPink else GallerySoftPink,
        border = if (isSelected) BorderStroke(1.dp, GalleryPink) else null,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (isSelected) Color.White else GalleryDeepPlum,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White else GalleryMutedPlum,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionTitle(): Unit {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "최근 결과",
            style = MaterialTheme.typography.titleLarge,
            color = GalleryDeepPlum,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "카드를 누르면 결과 상세로 이동합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = GalleryMutedPlum,
        )
    }
}

@Composable
private fun GalleryResultCard(
    result: GalleryResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tossClickable(
                role = Role.Button,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        border = BorderStroke(1.dp, GallerySoftPink),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResultThumbnail(result = result)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoryStatusBadge(status = result.status)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = result.dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = GalleryMutedPlum,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = GalleryDeepPlum,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = GalleryMutedPlum,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Map,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = GalleryPink,
                    )
                    Text(
                        text = result.location,
                        style = MaterialTheme.typography.labelMedium,
                        color = GalleryPink,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultThumbnail(
    result: GalleryResult,
    modifier: Modifier = Modifier,
): Unit {
    Box(
        modifier = modifier
            .size(width = 82.dp, height = 100.dp)
            .background(
                brush = Brush.verticalGradient(result.colors),
                shape = RoundedCornerShape(22.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color.White,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = GalleryPink,
                    )
                }
            }
            Text(
                text = result.progressLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoryStatusBadge(
    status: GalleryStatus,
    modifier: Modifier = Modifier,
): Unit {
    val badge: StatusBadge = when (status) {
        GalleryStatus.Processing -> StatusBadge(
            label = "처리중",
            icon = Icons.Rounded.Refresh,
            containerColor = GallerySoftYellow,
            contentColor = Color(0xFF8B5B00),
        )
        GalleryStatus.Completed -> StatusBadge(
            label = "완료",
            icon = Icons.Rounded.CheckCircle,
            containerColor = GallerySoftPink,
            contentColor = GalleryPink,
        )
        GalleryStatus.Failed -> StatusBadge(
            label = "실패",
            icon = Icons.Rounded.Refresh,
            containerColor = GallerySoftPink,
            contentColor = Color(0xFF9B243F),
        )
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = badge.containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = badge.icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = badge.contentColor,
            )
            Text(
                text = badge.label,
                style = MaterialTheme.typography.labelSmall,
                color = badge.contentColor,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun GalleryStatusPill(
    text: String,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = GalleryMutedPlum,
        )
    }
}

enum class GalleryStatus {
    Processing,
    Completed,
    Failed,
}

private enum class GalleryFilter {
    All,
    Completed,
    InProgress,
}

data class GalleryResult(
    val id: Int,
    val ebookId: String,
    val title: String,
    val location: String,
    val dateLabel: String,
    val summary: String,
    val status: GalleryStatus,
    val progressLabel: String,
    val colors: List<Color>,
)

private data class StatusBadge(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
)

private fun List<GalleryResult>.filterBy(filter: GalleryFilter): List<GalleryResult> =
    when (filter) {
        GalleryFilter.All -> this
        GalleryFilter.Completed -> filter { result -> result.status == GalleryStatus.Completed }
        GalleryFilter.InProgress -> emptyList()
    }

private fun com.ssafy.culture.domain.model.StoryProgress.toPlaceCount(): Int =
    chapters.map(StoryProgressChapter::placeTitle).distinct().size

private fun StoryQuestSnapshot.isInProgress(): Boolean =
    story.status == StoryStatus.InProgress || currentChapter != null

private val GalleryBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFB7C9),
        Color(0xFFFFDCE8),
        Color(0xFFFFF4CE),
    ),
)
private val GalleryPink: Color = Color(0xFFE94A7B)
private val GallerySoftPink: Color = Color(0xFFFFE2EB)
private val GalleryYellow: Color = Color(0xFFFFCF54)
private val GallerySoftYellow: Color = Color(0xFFFFF4CE)
private val GalleryDeepPlum: Color = Color(0xFF35121F)
private val GalleryMutedPlum: Color = Color(0xFF7A5565)
private val GalleryCardPalettes: List<List<Color>> = listOf(
    listOf(Color(0xFFFF8DB3), Color(0xFFFFCF54)),
    listOf(Color(0xFFE94A7B), Color(0xFFFFB7C9)),
    listOf(Color(0xFFFFC845), Color(0xFFFF9EBC)),
)

private fun EbookResult.toGalleryResult(index: Int): GalleryResult {
    val galleryStatus: GalleryStatus = status.toGalleryStatus()
    return GalleryResult(
        id = fileId.toStablePositiveId(fallback = index + 1),
        ebookId = fileId.ifBlank { "latest" },
        title = title.ifBlank { "문화 여행 이야기" },
        location = createGalleryLocation(),
        dateLabel = createdAt.toDateLabel(),
        summary = galleryStatus.toSummary(),
        status = galleryStatus,
        progressLabel = galleryStatus.toProgressLabel(),
        colors = GalleryCardPalettes[index % GalleryCardPalettes.size],
    )
}

private fun EbookStatus.toGalleryStatus(): GalleryStatus =
    when (this) {
        EbookStatus.Processing -> GalleryStatus.Processing
        EbookStatus.Completed -> GalleryStatus.Completed
        EbookStatus.Failed -> GalleryStatus.Failed
    }

private fun GalleryStatus.toSummary(): String =
    when (this) {
        GalleryStatus.Processing -> "사진과 장소 기록을 엮어 이야기로 만들고 있어요."
        GalleryStatus.Completed -> "사진과 장소가 담긴 결과물이 준비됐어요."
        GalleryStatus.Failed -> "결과물 생성에 실패했어요. 다시 시도해 주세요."
    }

private fun GalleryStatus.toProgressLabel(): String =
    when (this) {
        GalleryStatus.Processing -> "생성중"
        GalleryStatus.Completed -> "완성"
        GalleryStatus.Failed -> "실패"
    }

private fun EbookResult.createGalleryLocation(): String =
    when {
        title.contains("덕수궁") -> "덕수궁 돌담길"
        title.contains("북촌") -> "북촌 한옥마을"
        title.contains("한강") -> "반포한강공원"
        title.contains("경주") -> "경주 문화여행"
        else -> "문화 여행 기록"
    }

private fun String.toDateLabel(): String {
    if (isBlank()) return "방금 전"
    val date = substringBefore("T")
    val time = substringAfter("T", missingDelimiterValue = "")
    val dateParts = date.split("-")
    val month = dateParts.getOrNull(1)?.toIntOrNull()
    val day = dateParts.getOrNull(2)?.toIntOrNull()
    if (month == null || day == null) {
        return date.ifBlank { "방금 전" }
    }
    val timeLabel = time.take(5).takeIf { value -> value.length == 5 }
    return if (timeLabel != null) {
        "${month}월 ${day}일 $timeLabel"
    } else {
        "${month}월 ${day}일"
    }
}

private fun String.toStablePositiveId(fallback: Int): Int {
    if (isBlank()) return fallback
    val stableId = hashCode() and Int.MAX_VALUE
    return if (stableId == 0) fallback else stableId
}
