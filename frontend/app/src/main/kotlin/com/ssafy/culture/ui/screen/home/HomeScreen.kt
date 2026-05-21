package com.ssafy.culture.ui.screen.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.R
import com.ssafy.culture.data.auth.AuthTokenStore
import com.ssafy.culture.data.ebook.EbookRepository
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.EbookResult
import com.ssafy.culture.domain.model.EbookStatus
import com.ssafy.culture.domain.model.StoryProgressChapter
import com.ssafy.culture.domain.model.StoryQuestSnapshot
import com.ssafy.culture.ui.component.CultureAsyncImage
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.component.StatusPill
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import com.ssafy.culture.ui.theme.CultureBackgroundGradient
import com.ssafy.culture.ui.theme.CultureButtonDisabled
import com.ssafy.culture.ui.theme.CultureChipBg
import com.ssafy.culture.ui.theme.CultureChipInk
import com.ssafy.culture.ui.theme.CultureIconMuted
import com.ssafy.culture.ui.theme.CultureInkMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val items: List<EbookResult> = emptyList(),
    val todayCourse: TodayCourseUi? = null,
    val isLoading: Boolean = true,
    val isCourseLoading: Boolean = true,
    val errorMessage: String? = null,
    val nickname: String = "",
)

data class TodayCourseUi(
    val title: String,
    val subtitle: String,
    val placeCount: Int,
    val completedQuestCount: Int,
    val totalQuestCount: Int,
    val hasActiveStory: Boolean,
    val stops: List<TodayCourseStopUi>,
)

data class TodayCourseStopUi(
    val name: String,
    val caption: String,
    val completed: Boolean,
)

private const val HomeMemoryPreviewCount: Int = 2

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val ebookRepository: EbookRepository,
    private val storyRepository: StoryRepository,
    private val authTokenStore: AuthTokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(nickname = authTokenStore.getNickname().orEmpty()),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isCourseLoading = true,
                    errorMessage = null,
                )
            }
            val ebookResult = runCatching { ebookRepository.getEbooks().withPreviewDetails() }
            val courseResult = runCatching {
                storyRepository.getCurrentQuestSnapshot()
                    ?.takeIf { snapshot -> snapshot.currentChapter != null }
                    ?.toTodayCourseUi()
            }
            _uiState.update { state ->
                state.copy(
                    items = ebookResult.getOrDefault(state.items),
                    todayCourse = courseResult.getOrNull(),
                    nickname = authTokenStore.getNickname().orEmpty(),
                    isLoading = false,
                    isCourseLoading = false,
                    errorMessage = when {
                        ebookResult.isFailure -> "새로운 기록을 불러오지 못했어요."
                        courseResult.isFailure -> "오늘의 동선을 불러오지 못했어요."
                        else -> null
                    },
                )
            }
        }
    }

    private suspend fun List<EbookResult>.withPreviewDetails(): List<EbookResult> =
        mapIndexed { index, result ->
            if (index >= HomeMemoryPreviewCount || result.hasHomeThumbnail() || result.fileId.isBlank()) {
                result
            } else {
                runCatching { ebookRepository.getEbookDetail(result.fileId) }.getOrDefault(result)
            }
        }

}

@Composable
fun HomeRoute(
    onOpenCourseSelect: () -> Unit,
    onOpenCurrentQuest: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenResultDetail: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refresh()
    }
    HomeScreen(
        uiState = uiState,
        onOpenCourseSelect = onOpenCourseSelect,
        onOpenCurrentQuest = onOpenCurrentQuest,
        onOpenMap = onOpenMap,
        onOpenStory = onOpenStory,
        onOpenProfile = onOpenProfile,
        onOpenResultDetail = onOpenResultDetail,
    )
}

@Composable
private fun HomeScreen(
    uiState: HomeUiState,
    onOpenCourseSelect: () -> Unit,
    onOpenCurrentQuest: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenResultDetail: (String) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                selectedDestination = MainDestination.Home,
                onHomeClick = {},
                onMapClick = onOpenMap,
                onStoryClick = onOpenStory,
                onProfileClick = onOpenProfile,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CultureBackgroundGradient),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 28.dp,
                    end = 24.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item {
                    HomeHeader(
                        nickname = uiState.nickname,
                    )
                }

                item {
                    TodayCoursePanel(
                        todayCourse = uiState.todayCourse,
                        isLoading = uiState.isCourseLoading,
                        onStartStory = onOpenCourseSelect,
                        onContinueStory = onOpenCurrentQuest,
                    )
                }

                item {
                    CoursePathPanel(
                        todayCourse = uiState.todayCourse,
                    )
                }

                item {
                    SectionHeader(
                        title = "나의 추억 살펴보기",
                        subtitle = "완성한 이야기를 다시 펼쳐보기",
                    )
                }

                item {
                    MemoryGrid(
                        items = uiState.items.take(HomeMemoryPreviewCount),
                        onOpenDetail = onOpenResultDetail,
                    )
                }

                if (uiState.isLoading && uiState.items.isEmpty()) {
                    item {
                        StatusPill(text = "새로운 추억을 준비하는 중")
                    }
                }

                if (uiState.errorMessage != null) {
                    item {
                        StatusPill(text = uiState.errorMessage)
                    }
                }
            }
        }
    }
}


@Composable
private fun HomeHeader(
    nickname: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = nickname.takeIf(String::isNotBlank)?.let { "안녕, ${it}님" } ?: "안녕,",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "꿈과 이야기가 있는\n문화재 여행",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun TodayCoursePanel(
    todayCourse: TodayCourseUi?,
    isLoading: Boolean,
    onStartStory: () -> Unit,
    onContinueStory: () -> Unit,
) {
    val hasActiveStory: Boolean = todayCourse?.hasActiveStory == true
    val actionLabel: String = when {
        isLoading -> "동선 확인 중"
        hasActiveStory -> "이야기 이어가기"
        else -> "스토리 만들기"
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = todayCourse?.title ?: "오늘의 경주 코스",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = todayCourse?.subtitle ?: "새 스토리를 만들면 오늘의 동선을 볼 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CultureInkMuted,
                    )
                }
                val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                Image(
                    painter = painterResource(id = R.drawable.atoria_mascot),
                    contentDescription = null,
                    modifier = Modifier
                        .size(96.dp)
                        .graphicsLayer { scaleX = if (isRtl) 1f else -1f },
                    contentScale = ContentScale.Fit,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CourseBadge(
                    text = todayCourse?.let { "${it.placeCount}개 장소" } ?: "동선 준비",
                    modifier = Modifier.weight(1f),
                )
                CourseBadge(
                    text = todayCourse?.let { "${it.completedQuestCount}/${it.totalQuestCount} 완료" } ?: "진행 없음",
                    modifier = Modifier.weight(1f),
                )
                CourseBadge(
                    text = todayCourse?.let { "퀘스트 ${it.totalQuestCount}개" } ?: "퀘스트 준비",
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = if (hasActiveStory) onContinueStory else onStartStory,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = Color(0xFF6C2C3D),
                    disabledContainerColor = CultureButtonDisabled,
                    disabledContentColor = CultureInkMuted,
                ),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 0.dp,
                    pressedElevation = 0.dp,
                    disabledElevation = 0.dp,
                    hoveredElevation = 0.dp,
                    focusedElevation = 0.dp,
                ),
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun CourseBadge(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(50),
        color = CultureChipBg,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = CultureChipInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CoursePathPanel(
    todayCourse: TodayCourseUi?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeader(
                title = "오늘의 동선",
                subtitle = todayCourse?.subtitle ?: "진행 중인 이야기가 생기면 여기에 표시돼요.",
            )
            if (todayCourse == null || todayCourse.stops.isEmpty()) {
                Text(
                    text = "아직 진행 중인 동선이 없어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CultureInkMuted,
                )
            } else {
                todayCourse.stops.forEach { stop ->
                    CourseStopRow(
                        name = stop.name,
                        caption = stop.caption,
                        completed = stop.completed,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseStopRow(
    name: String,
    caption: String,
    completed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = if (completed) MaterialTheme.colorScheme.primary else CultureIconMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.labelMedium,
            color = if (completed) MaterialTheme.colorScheme.primary else CultureInkMuted,
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = CultureInkMuted,
        )
    }
}

@Composable
private fun MemoryGrid(
    items: List<EbookResult>,
    onOpenDetail: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MemoryCard(
            item = items.getOrNull(0),
            fallbackTitle = "천마총의 별빛",
            fallbackCaption = "첫 번째 이야기",
            modifier = Modifier.weight(1f),
            onOpenDetail = onOpenDetail,
        )
        MemoryCard(
            item = items.getOrNull(1),
            fallbackTitle = "대릉원 산책",
            fallbackCaption = "완성한 기록",
            modifier = Modifier.weight(1f),
            onOpenDetail = onOpenDetail,
        )
    }
}

@Composable
private fun MemoryCard(
    item: EbookResult?,
    fallbackTitle: String,
    fallbackCaption: String,
    modifier: Modifier = Modifier,
    onOpenDetail: (String) -> Unit,
) {
    val resultId: String? = item?.fileId?.takeIf(String::isNotBlank)
    val thumbnailUrl: String? = item?.homeThumbnailUrl()
    val hasArtwork: Boolean = thumbnailUrl != null

    Surface(
        modifier = modifier
            .height(154.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (resultId != null) {
                    Modifier.tossClickable(
                        pressedScale = CultureMotion.SubtlePressedScale,
                    ) {
                        onOpenDetail(resultId)
                    }
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnailUrl != null) {
                CultureAsyncImage(
                    model = thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xCC35121F),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFFEDF4),
                                    Color(0xFFFFF5D8),
                                ),
                            ),
                        ),
                )
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .size(30.dp),
                    tint = Color(0xFFFFC845),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
            ) {
                Text(
                    text = item?.title ?: fallbackTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (hasArtwork) Color.White else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item?.toHomeCaption() ?: fallbackCaption,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasArtwork) Color.White.copy(alpha = 0.86f) else Color(0xFF8D3C56),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun EbookResult.hasHomeThumbnail(): Boolean =
    homeThumbnailUrl() != null

private fun EbookResult.homeThumbnailUrl(): String? =
    thumbnailUrl.takeIf(String::isHttpUrl)
        ?: metadata?.content?.pages?.firstNotNullOfOrNull { page ->
            page.imageUrl?.takeIf(String::isHttpUrl)
        }

private fun EbookResult.toHomeCaption(): String =
    when (status) {
        EbookStatus.Processing -> "생성 중"
        EbookStatus.Completed -> metadata?.pageCount?.let { pageCount -> "${pageCount}쪽 완성본" }
            ?: "완성된 결과물"
        EbookStatus.Failed -> "생성 실패"
    }

private fun StoryQuestSnapshot.toTodayCourseUi(): TodayCourseUi {
    val currentChapterId: Long? = currentChapter?.chapterId
    val stops: List<TodayCourseStopUi> = progress.chapters.toCourseStops(currentChapterId)
    val currentPlace: String = currentChapter?.placeTitle?.takeIf(String::isNotBlank)
        ?: stops.firstOrNull { stop -> !stop.completed }?.name
        ?: stops.lastOrNull()?.name
        ?: "현재 장소"
    return TodayCourseUi(
        title = story.title.ifBlank { "진행 중인 경주 코스" },
        subtitle = "$currentPlace 이어가기",
        placeCount = stops.size,
        completedQuestCount = progress.completedCount,
        totalQuestCount = progress.totalCount,
        hasActiveStory = currentChapter != null,
        stops = stops,
    )
}

private fun List<StoryProgressChapter>.toCourseStops(
    currentChapterId: Long?,
): List<TodayCourseStopUi> =
    filter { chapter -> chapter.placeTitle.isNotBlank() }
        .groupBy(StoryProgressChapter::placeTitle)
        .map { (placeTitle, chapters) ->
            val completed: Boolean = chapters.all(StoryProgressChapter::isCompleted)
            val isCurrent: Boolean = chapters.any { chapter -> chapter.chapterId == currentChapterId }
            TodayCourseStopUi(
                name = placeTitle,
                caption = when {
                    completed -> "완료"
                    isCurrent -> "진행 중"
                    else -> "다음 퀘스트"
                },
                completed = completed,
            )
        }

private fun String.isHttpUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

