package com.ssafy.culture.ui.screen.quest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.R
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.StoryChapterDetail
import com.ssafy.culture.domain.model.StoryProgress
import com.ssafy.culture.domain.model.StoryProgressChapter
import com.ssafy.culture.domain.model.StoryQuestSnapshot
import com.ssafy.culture.ui.component.InfoPill
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.component.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class QuestRouteUiState(
    val snapshot: StoryQuestSnapshot? = null,
    val currentChapterDetail: StoryChapterDetail? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

private data class QuestRouteLoadResult(
    val snapshot: StoryQuestSnapshot?,
    val currentChapterDetail: StoryChapterDetail?,
)

@HiltViewModel
class QuestRouteViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {
    private val _uiState: MutableStateFlow<QuestRouteUiState> = MutableStateFlow(QuestRouteUiState())
    val uiState: StateFlow<QuestRouteUiState> = _uiState.asStateFlow()

    init {
        loadQuest()
    }

    fun loadQuest() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val snapshot: StoryQuestSnapshot? = storyRepository.getCurrentQuestSnapshot()
                val chapterDetail: StoryChapterDetail? = snapshot?.currentChapter?.let { chapter ->
                    storyRepository.getChapterDetail(
                        storyId = snapshot.story.storyId,
                        chapterId = chapter.chapterId,
                    )
                }
                QuestRouteLoadResult(
                    snapshot = snapshot,
                    currentChapterDetail = chapterDetail,
                )
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        snapshot = result.snapshot,
                        currentChapterDetail = result.currentChapterDetail,
                        isLoading = false,
                        errorMessage = if (result.snapshot == null) "진행 중인 스토리가 아직 없어요." else null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        currentChapterDetail = null,
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "스토리 진행 정보를 불러오지 못했어요.",
                    )
                }
            }
        }
    }
}

@Composable
fun QuestRoute(
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenQuestDetail: () -> Unit,
    viewModel: QuestRouteViewModel = hiltViewModel(),
) {
    val uiState: QuestRouteUiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.loadQuest()
    }
    QuestRouteScreen(
        uiState = uiState,
        onBack = onBack,
        onOpenHome = onOpenHome,
        onOpenMap = onOpenMap,
        onOpenProfile = onOpenProfile,
        onOpenQuestDetail = onOpenQuestDetail,
    )
}

@Composable
private fun QuestRouteScreen(
    uiState: QuestRouteUiState,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenQuestDetail: () -> Unit,
) {
    val context: Context = LocalContext.current
    val arrivalTarget: ArrivalMissionTarget? = uiState.currentChapterDetail?.toArrivalMissionTarget()
    var currentLocation: Location? by remember {
        mutableStateOf(null)
    }
    var hasRequestedLocationPermission: Boolean by rememberSaveable {
        mutableStateOf(false)
    }
    var isLocationPermissionGranted: Boolean by remember {
        mutableStateOf(hasQuestLocationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasRequestedLocationPermission = true
        isLocationPermissionGranted = permissions.values.any { isGranted -> isGranted }
    }
    LaunchedEffect(context) {
        isLocationPermissionGranted = hasQuestLocationPermission(context)
    }
    LaunchedEffect(arrivalTarget, hasRequestedLocationPermission, isLocationPermissionGranted) {
        if (arrivalTarget != null && !isLocationPermissionGranted && !hasRequestedLocationPermission) {
            hasRequestedLocationPermission = true
            permissionLauncher.launch(QuestLocationPermissions)
        }
    }
    LaunchedEffect(context, arrivalTarget, isLocationPermissionGranted) {
        currentLocation = null
        if (arrivalTarget != null && isLocationPermissionGranted) {
            currentLocation = resolveQuestLastKnownLocation(context)
            requestQuestCurrentLocation(context)?.let { location ->
                currentLocation = location
            }
        }
    }
    ObserveMissionArrivalLocation(
        target = if (isLocationPermissionGranted) arrivalTarget else null,
        onLocationChanged = { location ->
            currentLocation = location
        },
    )
    val arrivalDistanceMeters: Float? = if (arrivalTarget == null) {
        null
    } else {
        currentLocation?.distanceTo(arrivalTarget)
    }
    val arrivalRadiusMeters: Float? = arrivalTarget?.arrivalRadiusMeters()
    val hasArrived: Boolean = arrivalDistanceMeters != null &&
        arrivalRadiusMeters != null &&
        arrivalDistanceMeters <= arrivalRadiusMeters
    val arrivedTarget: ArrivalMissionTarget? = arrivalTarget?.takeIf { hasArrived }
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
                .background(QuestBackgroundBrush),
        ) {
            QuestBackdrop()
            if (arrivedTarget != null) {
                ArrivalMissionContent(
                    placeName = arrivedTarget.placeName,
                    onMissionStartClick = onOpenQuestDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 22.dp,
                        top = 28.dp,
                        end = 22.dp,
                        bottom = 26.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        QuestHeader(onBack = onBack)
                    }
                    item {
                        CourseActionCard(
                            snapshot = uiState.snapshot,
                            isLoading = uiState.isLoading,
                            onOpenQuestDetail = onOpenQuestDetail,
                        )
                    }
                    item {
                        QuestRouteMap(
                            progress = uiState.snapshot?.progress,
                            onOpenQuestDetail = onOpenQuestDetail,
                        )
                    }
                    item {
                        ProgressCheckCard(progress = uiState.snapshot?.progress)
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
}

@Composable
private fun ArrivalMissionContent(
    placeName: String,
    onMissionStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(
        modifier = modifier
            .padding(horizontal = if (isLandscape) 22.dp else 28.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 154.dp else 288.dp),
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFFFFD8E4),
            shadowElevation = 8.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.74f),
                                Color(0xFFFFBED3),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.atoria_mascot),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(210.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 44.dp))
        Text(
            text = "${placeName}에\n도착했어요!",
            style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
            color = Color(0xFF151015),
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(if (isLandscape) 14.dp else 40.dp))
        Button(
            onClick = onMissionStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLandscape) 54.dp else 64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
        ) {
            Text(
                text = "미션 시작하기",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun QuestBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(18) { index ->
            val x: Float = ((index * 67) % 100) / 100f * size.width
            val y: Float = ((index * 41) % 100) / 100f * size.height
            val length: Float = 8f + (index % 4) * 3f
            drawLine(
                color = Color.White.copy(alpha = 0.44f),
                start = Offset(x - length, y),
                end = Offset(x + length, y),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.44f),
                start = Offset(x, y - length),
                end = Offset(x, y + length),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun QuestHeader(
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 6.dp,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Text(
            text = "오늘의 미션 경로",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF7A314A),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "핑크빛 노드를 따라\n스토리를 완성해요",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "코스에 숨은 체크 포인트를 순서대로 열어보세요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseActionCard(
    snapshot: StoryQuestSnapshot?,
    isLoading: Boolean,
    onOpenQuestDetail: () -> Unit,
) {
    val protagonistName: String = snapshot?.story?.protagonists
        ?.firstOrNull()
        ?.name
        ?.takeIf(String::isNotBlank)
        ?: "주인공"
    val courseStops: List<QuestStop> = snapshot?.progress.toPlaceStops()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp)),
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${protagonistName}의 코스",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = snapshot?.currentChapter?.let { chapter ->
                            "${chapter.sequence}번째 미션을 이어 진행할 차례예요"
                        } ?: "새로운 미션 경로를 준비하고 있어요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            CourseStopGrid(stops = courseStops)
            ProgressTrack(progress = snapshot?.progress)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InfoPill(
                    text = "${snapshot?.progress?.toPlaceStops()?.size ?: 0}곳",
                    modifier = Modifier.weight(1f),
                )
                InfoPill(
                    text = "${snapshot?.progress?.completedCount ?: 0}개 완료",
                    modifier = Modifier.weight(1f),
                )
                InfoPill(
                    text = snapshot?.currentChapter?.placeTitle ?: "대기 중",
                    modifier = Modifier.weight(1f),
                    height = null,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                    verticalPadding = 8.dp,
                )
            }
            Button(
                onClick = onOpenQuestDetail,
                enabled = snapshot != null && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFFFC9D8),
                    disabledContentColor = Color.White,
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
                )
            }
        }
    }
}

@Composable
private fun CourseStopGrid(
    stops: List<QuestStop>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFFFFF7FA),
        border = BorderStroke(1.dp, Color(0xFFFFD9E6)),
    ) {
        if (stops.isEmpty()) {
            Text(
                text = "코스를 불러오는 중이에요",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stops.chunked(2).forEach { rowStops ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowStops.forEach { stop ->
                            CourseStopCell(
                                stop = stop,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (rowStops.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseStopCell(
    stop: QuestStop,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(16.dp),
        color = stop.status.legendColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuestStatusMarker(status = stop.status, size = 22.dp, iconSize = 14.dp)
            Text(
                text = "${stop.order}. ${stop.title}",
                style = MaterialTheme.typography.bodySmall,
                color = stop.status.titleColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressTrack(progress: StoryProgress?) {
    val totalCount: Int = progress?.totalCount ?: 0
    val completedCount: Int = progress?.completedCount ?: 0
    val progressRate: Float = (progress?.progressRate ?: if (totalCount > 0) {
        completedCount.toFloat() / totalCount.toFloat()
    } else {
        0f
    }).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "진행률",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "$completedCount / $totalCount 완료",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFFFE4ED)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressRate)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(RouteCompletedColor),
            )
        }
    }
}

@Composable
private fun QuestRouteMap(
    progress: StoryProgress?,
    onOpenQuestDetail: () -> Unit,
) {
    val stops: List<QuestStop> = progress.toPlaceStops()
    val highlightedSegmentCount: Int = stops.indexOfFirst { stop ->
        stop.status == QuestStopStatus.Current
    }.let { currentIndex: Int ->
        when {
            currentIndex >= 0 -> currentIndex
            stops.isNotEmpty() -> stops.lastIndex
            else -> 0
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.White,
        shadowElevation = 7.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RouteSectionHeader()
            RouteLegendRow()
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(430.dp),
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val points: List<Offset> = stops.map { stop ->
                        Offset(size.width * stop.xPercent, size.height * stop.yPercent)
                    }
                    points.zipWithNext().forEach { (start, end) ->
                        drawLine(
                            color = Color.White.copy(alpha = 0.94f),
                            start = start,
                            end = end,
                            strokeWidth = 22f,
                            cap = StrokeCap.Round,
                        )
                    }
                    points.zipWithNext().forEachIndexed { index, (start, end) ->
                        val isHighlightedSegment: Boolean = index < highlightedSegmentCount
                        drawLine(
                            color = if (isHighlightedSegment) RouteCompletedColor else RouteLockedLineColor,
                            start = start,
                            end = end,
                            strokeWidth = if (isHighlightedSegment) 12f else 8f,
                            cap = StrokeCap.Round,
                        )
                    }
                    points.forEach { point ->
                        drawCircle(
                            color = Color.White.copy(alpha = 0.82f),
                            radius = 31f,
                            center = point,
                            style = Stroke(width = 7f),
                        )
                    }
                }
                if (stops.isEmpty()) {
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "미션 경로를 준비하고 있어요",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                stops.forEach { stop ->
                    QuestNode(
                        stop = stop,
                        onOpenQuestDetail = onOpenQuestDetail,
                        modifier = Modifier.offset(
                            x = ((maxWidth * stop.xPercent) - RouteNodeWidth / 2)
                                .coerceIn(0.dp, maxWidth - RouteNodeWidth),
                            y = ((maxHeight * stop.yPercent) - RouteMarkerSize / 2)
                                .coerceIn(0.dp, maxHeight - RouteNodeHeight),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSectionHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "미션 수행 경로",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "완료는 체크, 현재는 !, 예정지는 ?로 보여줘요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteLegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuestLegendItem(status = QuestStopStatus.Completed, label = "완료", modifier = Modifier.weight(1f))
        QuestLegendItem(status = QuestStopStatus.Current, label = "진행 중", modifier = Modifier.weight(1f))
        QuestLegendItem(status = QuestStopStatus.Locked, label = "예정", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuestLegendItem(
    status: QuestStopStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(14.dp),
        color = status.legendColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuestStatusMarker(status = status, size = 20.dp, iconSize = 14.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = status.captionColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuestNode(
    stop: QuestStop,
    onOpenQuestDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled: Boolean = stop.status == QuestStopStatus.Current
    Column(
        modifier = modifier
            .width(RouteNodeWidth)
            .height(RouteNodeHeight)
            .then(if (isEnabled) Modifier.clickable(onClick = onOpenQuestDetail) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuestStatusMarker(status = stop.status, size = RouteMarkerSize, iconSize = 28.dp)
        Text(
            text = stop.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 15.sp,
            ),
            color = stop.status.titleColor,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuestStatusMarker(
    status: QuestStopStatus,
    size: Dp,
    iconSize: Dp,
) {
    val borderWidth: Dp = if (size <= 24.dp) 1.5.dp else 3.dp
    val markerElevation: Dp = if (size <= 30.dp) 0.dp else status.shadowElevation
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = status.nodeColor,
        border = BorderStroke(width = borderWidth, color = status.borderColor),
        shadowElevation = markerElevation,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (status == QuestStopStatus.Completed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "완료",
                    modifier = Modifier.size(iconSize),
                    tint = status.symbolColor,
                )
            } else {
                Text(
                    text = status.symbol,
                    style = if (size <= 30.dp) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.headlineSmall
                    },
                    color = status.symbolColor,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ProgressCheckCard(progress: StoryProgress?) {
    val checks: List<QuestCheck> = progress.toPlaceStops().map { stop ->
        QuestCheck(
            title = "${stop.order}. ${stop.title}",
            caption = stop.status.checkCaption,
            status = stop.status,
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "진행 체크",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (checks.isEmpty()) {
                Text(
                    text = "체크할 미션을 불러오는 중이에요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                checks.forEach { check ->
                    ProgressCheckRow(check = check)
                }
            }
        }
    }
}

@Composable
private fun ProgressCheckRow(check: QuestCheck) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuestStatusMarker(status = check.status, size = 30.dp, iconSize = 18.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = check.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = check.status.statusText,
            style = MaterialTheme.typography.bodySmall,
            color = check.status.captionColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun StoryProgress?.toPlaceStops(): List<QuestStop> {
    val chapters: List<StoryProgressChapter> = this?.chapters.orEmpty()
    val currentChapterIndex: Int = chapters.indexOfFirst { chapter -> !chapter.isCompleted }
    val groupedChapters: List<List<IndexedValue<StoryProgressChapter>>> = chapters.withIndex()
        .groupBy { indexedChapter -> indexedChapter.value.placeTitle }
        .values
        .toList()
    val positions: List<RouteNodePosition> = buildZigzagPositions(groupedChapters.size)
    return groupedChapters.mapIndexed { index, chaptersAtPlace ->
        val position: RouteNodePosition = positions[index]
        val isCompleted: Boolean = chaptersAtPlace.all { indexedChapter -> indexedChapter.value.isCompleted }
        val isCurrent: Boolean = chaptersAtPlace.any { indexedChapter ->
            indexedChapter.index == currentChapterIndex
        }
        QuestStop(
            order = index + 1,
            title = chaptersAtPlace.firstOrNull()?.value?.placeTitle.orEmpty(),
            status = when {
                isCompleted -> QuestStopStatus.Completed
                isCurrent -> QuestStopStatus.Current
                else -> QuestStopStatus.Locked
            },
            xPercent = position.xPercent,
            yPercent = position.yPercent,
        )
    }
}

@Composable
@SuppressLint("MissingPermission")
private fun ObserveMissionArrivalLocation(
    target: ArrivalMissionTarget?,
    onLocationChanged: (Location?) -> Unit,
) {
    val context: Context = LocalContext.current
    val latestOnLocationChanged by rememberUpdatedState(onLocationChanged)
    DisposableEffect(context, target) {
        if (target == null || !hasQuestLocationPermission(context)) {
            latestOnLocationChanged(null)
            return@DisposableEffect onDispose {}
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null || !LocationManagerCompat.isLocationEnabled(locationManager)) {
            latestOnLocationChanged(null)
            return@DisposableEffect onDispose {}
        }
        val provider: String? = selectQuestLocationProvider(context, locationManager)
        if (provider == null) {
            latestOnLocationChanged(null)
            return@DisposableEffect onDispose {}
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                latestOnLocationChanged(location)
            }
        }
        latestOnLocationChanged(resolveQuestLastKnownLocation(context, locationManager))
        runCatching {
            locationManager.requestLocationUpdates(
                provider,
                ArrivalLocationMinTimeMillis,
                ArrivalLocationMinDistanceMeters,
                listener,
                Looper.getMainLooper(),
            )
        }.onFailure {
            latestOnLocationChanged(null)
        }
        onDispose {
            locationManager.removeUpdates(listener)
        }
    }
}

private fun StoryChapterDetail.toArrivalMissionTarget(): ArrivalMissionTarget? {
    val latitude: Double = place.latitude ?: return null
    val longitude: Double = place.longitude ?: return null
    return ArrivalMissionTarget(
        placeName = place.title.ifBlank { "미션 장소" },
        latitude = latitude,
        longitude = longitude,
    )
}

private fun Location.distanceTo(target: ArrivalMissionTarget): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        latitude,
        longitude,
        target.latitude,
        target.longitude,
        results,
    )
    return results.first()
}

private fun ArrivalMissionTarget.arrivalRadiusMeters(): Float =
    if (isStrictGpsMissionPlace()) {
        StrictGpsArrivalRadiusMeters
    } else {
        MissionArrivalRadiusMeters
    }

private fun ArrivalMissionTarget.isStrictGpsMissionPlace(): Boolean =
    StrictGpsMissionPlaces.any { place ->
        place.distanceMetersTo(
            latitude = latitude,
            longitude = longitude,
        ) <= StrictGpsPlaceMatchToleranceMeters
    }

private fun StrictGpsMissionPlace.distanceMetersTo(
    latitude: Double,
    longitude: Double,
): Float {
    val results = FloatArray(1)
    Location.distanceBetween(
        this.latitude,
        this.longitude,
        latitude,
        longitude,
        results,
    )
    return results.first()
}

private fun hasQuestLocationPermission(context: Context): Boolean =
    QuestLocationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

private fun selectQuestLocationProvider(
    context: Context,
    locationManager: LocationManager,
): String? {
    val enabledProviders: List<String> = runCatching {
        locationManager.getProviders(true)
    }.getOrDefault(emptyList())
    if (enabledProviders.isEmpty()) return null
    val hasFineLocation: Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val preferredProviders: List<String> = if (hasFineLocation) {
        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    } else {
        listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
    }
    return preferredProviders.firstOrNull(enabledProviders::contains)
        ?: enabledProviders.firstOrNull()
}

private fun resolveQuestLastKnownLocation(context: Context): Location? {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) return null
    return resolveQuestLastKnownLocation(
        context = context,
        locationManager = locationManager,
    )
}

@SuppressLint("MissingPermission")
private fun resolveQuestLastKnownLocation(
    context: Context,
    locationManager: LocationManager,
): Location? {
    if (!hasQuestLocationPermission(context)) return null
    return runCatching {
        locationManager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter(Location::isRecentForQuest)
            .maxByOrNull(Location::getTime)
    }.getOrNull()
}

@SuppressLint("MissingPermission")
private suspend fun requestQuestCurrentLocation(context: Context): Location? {
    if (!hasQuestLocationPermission(context)) return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) return null
    val provider: String = selectQuestLocationProvider(context, locationManager) ?: return null
    return withTimeoutOrNull(ArrivalCurrentLocationFixTimeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    locationManager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }
            continuation.invokeOnCancellation {
                locationManager.removeUpdates(listener)
            }
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    0L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure {
                locationManager.removeUpdates(listener)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}

private fun Location.isRecentForQuest(): Boolean {
    val ageMillis: Long = System.currentTimeMillis() - time
    return time > 0L && ageMillis in 0..ArrivalRecentLocationMaxAgeMillis
}

private fun buildZigzagPositions(count: Int): List<RouteNodePosition> {
    if (count <= 0) return emptyList()
    if (count == 1) return listOf(RouteNodePosition(xPercent = 0.5f, yPercent = 0.5f))
    return List(count) { index ->
        RouteNodePosition(
            xPercent = if (index % 2 == 0) 0.24f else 0.76f,
            yPercent = 0.1f + (0.8f * index / (count - 1)),
        )
    }
}

private val QuestStopStatus.nodeColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> RouteCompletedColor
        QuestStopStatus.Current -> RouteCurrentColor
        QuestStopStatus.Locked -> Color(0xFFF2E7ED)
    }

private val QuestStopStatus.symbolColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> Color.White
        QuestStopStatus.Current -> Color(0xFF5B3B00)
        QuestStopStatus.Locked -> Color(0xFF7F6874)
    }

private val QuestStopStatus.borderColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> Color(0xFFE9FFF5)
        QuestStopStatus.Current -> Color(0xFFFFF0B4)
        QuestStopStatus.Locked -> Color.White
    }

private val QuestStopStatus.titleColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> Color(0xFF185F42)
        QuestStopStatus.Current -> Color(0xFF5B3B00)
        QuestStopStatus.Locked -> Color(0xFF6C5963)
    }

private val QuestStopStatus.captionColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> Color(0xFF168357)
        QuestStopStatus.Current -> Color(0xFF9A6B19)
        QuestStopStatus.Locked -> Color(0xFF8D6B76)
    }

private val QuestStopStatus.legendColor: Color
    get() = when (this) {
        QuestStopStatus.Completed -> Color(0xFFE9FFF5)
        QuestStopStatus.Current -> Color(0xFFFFF5CF)
        QuestStopStatus.Locked -> Color(0xFFF7EFF3)
    }

private val QuestStopStatus.shadowElevation: Dp
    get() = when (this) {
        QuestStopStatus.Completed -> 8.dp
        QuestStopStatus.Current -> 10.dp
        QuestStopStatus.Locked -> 3.dp
    }

private val QuestStopStatus.symbol: String
    get() = when (this) {
        QuestStopStatus.Completed -> "✓"
        QuestStopStatus.Current -> "!"
        QuestStopStatus.Locked -> "?"
    }

private val QuestStopStatus.statusText: String
    get() = when (this) {
        QuestStopStatus.Completed -> "완료"
        QuestStopStatus.Current -> "진행"
        QuestStopStatus.Locked -> "예정"
    }

private val QuestStopStatus.checkCaption: String
    get() = when (this) {
        QuestStopStatus.Completed -> "미션 수행 완료"
        QuestStopStatus.Current -> "현재 장소에서 미션 진행 중"
        QuestStopStatus.Locked -> "아직 방문 전이에요"
    }

private data class QuestStop(
    val order: Int,
    val title: String,
    val status: QuestStopStatus,
    val xPercent: Float,
    val yPercent: Float,
)

private data class QuestCheck(
    val title: String,
    val caption: String,
    val status: QuestStopStatus,
)

private data class ArrivalMissionTarget(
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
)

private data class StrictGpsMissionPlace(
    val latitude: Double,
    val longitude: Double,
)

private data class RouteNodePosition(
    val xPercent: Float,
    val yPercent: Float,
)

private enum class QuestStopStatus {
    Completed,
    Current,
    Locked,
}

private val QuestBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFAFC5),
        Color(0xFFFFDDE8),
        Color(0xFFFFF4D5),
    ),
)
private val RouteCompletedColor = Color(0xFF2DBE7F)
private val RouteCurrentColor = Color(0xFFFFCF54)
private val RouteLockedLineColor = Color(0xFFD8CDD4)
private val RouteMarkerSize = 58.dp
private val RouteNodeWidth = 124.dp
private val RouteNodeHeight = 110.dp
private val QuestLocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)
private const val MissionArrivalRadiusMeters = 150f
private val StrictGpsMissionPlaces = listOf(
    StrictGpsMissionPlace(latitude = 36.1071660, longitude = 128.4164430),
    StrictGpsMissionPlace(latitude = 36.1071590, longitude = 128.4162900),
)
private const val StrictGpsArrivalRadiusMeters = 10f
private const val StrictGpsPlaceMatchToleranceMeters = 5_000f
private const val ArrivalLocationMinTimeMillis = 5000L
private const val ArrivalLocationMinDistanceMeters = 10f
private const val ArrivalRecentLocationMaxAgeMillis = 120000L
private const val ArrivalCurrentLocationFixTimeoutMillis = 5000L
