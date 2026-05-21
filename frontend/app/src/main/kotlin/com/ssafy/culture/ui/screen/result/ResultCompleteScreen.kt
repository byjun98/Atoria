package com.ssafy.culture.ui.screen.result

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.culture.R
import com.ssafy.culture.data.ebook.EbookFileRepository
import com.ssafy.culture.data.ebook.EbookRepository
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.EbookGenerationResult
import com.ssafy.culture.domain.model.EbookResult
import com.ssafy.culture.domain.model.EbookStatus
import com.ssafy.culture.domain.model.EbookType
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.component.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ResultCompleteUiState(
    val result: EbookResult? = null,
    val isLoading: Boolean = true,
    val isAwaitingLocationWarning: Boolean = false,
    val isExporting: Boolean = false,
    val exportUri: Uri? = null,
    val exportFileName: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class ResultCompleteViewModel @Inject constructor(
    private val ebookRepository: EbookRepository,
    private val storyRepository: StoryRepository,
    private val ebookFileRepository: EbookFileRepository,
    private val routeHistoryStore: com.ssafy.culture.data.route.RouteHistoryStore,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ResultCompleteUiState())
    val uiState: StateFlow<ResultCompleteUiState> = _uiState.asStateFlow()
    private var hasLoaded: Boolean = false
    private var loadedStoryId: Long? = null
    private var locationWarningAcknowledgedStoryId: Long? = null

    fun loadResult(storyId: Long?) {
        if (hasLoaded && loadedStoryId == storyId) return
        hasLoaded = true
        loadedStoryId = storyId
        viewModelScope.launch {
            if (storyId != null &&
                locationWarningAcknowledgedStoryId != storyId &&
                storyRepository.hasUnverifiedMissionSubmissions(storyId)
            ) {
                _uiState.update { state ->
                    state.copy(
                        result = null,
                        isLoading = false,
                        isAwaitingLocationWarning = true,
                        errorMessage = null,
                    )
                }
                return@launch
            }
            _uiState.update { state ->
                state.copy(
                    isLoading = true,
                    isAwaitingLocationWarning = false,
                    errorMessage = null,
                )
            }
            runCatching {
                if (storyId == null) {
                    ebookRepository.getLatestResult()
                } else {
                    requestGeneratedResult(storyId)
                }
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(
                        result = result,
                        isLoading = false,
                        isAwaitingLocationWarning = false,
                    )
                }
            }.onFailure {
                hasLoaded = false
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = "완성된 결과물을 불러오지 못했어요.",
                    )
                }
            }
        }
    }

    private suspend fun requestGeneratedResult(storyId: Long): EbookResult {
        val story = storyRepository.getStoryDetail(storyId)
        val title = story.title.ifBlank { "나의 문화 여행 이야기" }
        val routePoints = routeHistoryStore.getRouteForStory(storyId)
        val options: Map<String, Any?> = if (routePoints.isEmpty()) {
            emptyMap()
        } else {
            mapOf(
                "routePoints" to routePoints.map { point ->
                    mapOf(
                        "latitude" to point.latitude,
                        "longitude" to point.longitude,
                        "timestamp" to point.timestamp,
                    )
                },
            )
        }
        val generation: EbookGenerationResult = ebookRepository.requestEbookGeneration(
            courseId = story.courseId,
            storyId = story.storyId,
            title = title,
            options = options,
        )
        if (generation.ebookId.isBlank()) {
            return generation.toPendingResult(title)
        }
        return pollGeneratedResult(
            ebookId = generation.ebookId,
            fallback = generation.toPendingResult(title),
        )
    }

    private suspend fun pollGeneratedResult(
        ebookId: String,
        fallback: EbookResult,
    ): EbookResult {
        var latestResult: EbookResult = fallback
        repeat(EbookPollMaxAttempts) { attempt ->
            if (attempt > 0) delay(EbookPollIntervalMillis)
            latestResult = runCatching {
                ebookRepository.getEbookDetail(ebookId)
            }.getOrElse {
                latestResult
            }
            if (latestResult.hasFinishedGenerating()) {
                return latestResult
            }
        }
        return latestResult
    }

    fun refreshCurrentResult() {
        val currentResult: EbookResult = _uiState.value.result
            ?: return loadFreshResult()
        if (currentResult.fileId.isBlank()) {
            loadFreshResult()
            return
        }
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                pollGeneratedResult(
                    ebookId = currentResult.fileId,
                    fallback = currentResult,
                )
            }.onSuccess { result ->
                _uiState.update { state ->
                    state.copy(result = result, isLoading = false)
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isAwaitingLocationWarning = false,
                        errorMessage = "?꾩꽦??寃곌낵臾쇱쓣 遺덈윭?ㅼ? 紐삵뻽?댁슂.",
                    )
                }
            }
        }
    }

    fun continueWithUnverifiedLocations() {
        val storyId: Long = loadedStoryId ?: return
        locationWarningAcknowledgedStoryId = storyId
        hasLoaded = false
        loadResult(storyId)
    }

    private fun loadFreshResult() {
        hasLoaded = false
        loadResult(loadedStoryId)
    }

    fun exportEbook() {
        val result: EbookResult = _uiState.value.result ?: return
        if (!result.canExportEbook()) {
            _uiState.update { state ->
                state.copy(errorMessage = "완성된 eBook 파일이 아직 준비되지 않았어요.")
            }
            return
        }
        if (_uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isExporting = true, errorMessage = null)
            }
            runCatching {
                val file = ebookFileRepository.getLocalPdfFile(result)
                ebookFileRepository.getShareUri(file) to file.name
            }.onSuccess { (uri, fileName) ->
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        exportUri = uri,
                        exportFileName = fileName,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isExporting = false,
                        errorMessage = throwable.localizedMessage ?: "eBook 내보내기에 실패했어요.",
                    )
                }
            }
        }
    }

    fun consumeExportEvent() {
        _uiState.update { state ->
            state.copy(exportUri = null, exportFileName = null)
        }
    }
}

@Composable
fun ResultCompleteRoute(
    storyId: Long? = null,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    onReadNow: (String) -> Unit,
    viewModel: ResultCompleteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(storyId) {
        viewModel.loadResult(storyId)
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(uiState.exportUri) {
        val uri: Uri = uiState.exportUri ?: return@LaunchedEffect
        val exportIntent = Intent(Intent.ACTION_SEND).apply {
            type = PdfMimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, uiState.exportFileName ?: "ebook.pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(exportIntent, "eBook 내보내기"))
        viewModel.consumeExportEvent()
    }
    ResultCompleteScreen(
        uiState = uiState,
        onOpenHome = onOpenHome,
        onOpenMap = onOpenMap,
        onOpenStory = onOpenStory,
        onOpenProfile = onOpenProfile,
        onReadNow = onReadNow,
        onExport = viewModel::exportEbook,
        onRetry = viewModel::refreshCurrentResult,
        onContinueWithUnverifiedLocations = viewModel::continueWithUnverifiedLocations,
    )
}

private fun EbookGenerationResult.toPendingResult(title: String): EbookResult =
    EbookResult(
        fileId = ebookId,
        type = EbookType.Ebook,
        title = title,
        fileUrl = "",
        thumbnailUrl = "",
        status = status.takeUnless { ebookId.isBlank() } ?: EbookStatus.Processing,
        createdAt = "",
        metadata = null,
    )

@Composable
private fun ResultCompleteScreen(
    uiState: ResultCompleteUiState,
    onOpenHome: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    onReadNow: (String) -> Unit,
    onExport: () -> Unit,
    onRetry: () -> Unit,
    onContinueWithUnverifiedLocations: () -> Unit,
) {
    val result: EbookResult? = uiState.result
    val canOpenEbook: Boolean = result.canOpenEbook()
    val canExportEbook: Boolean = result.canExportEbook()
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            MainBottomBar(
                selectedDestination = MainDestination.Story,
                onHomeClick = onOpenHome,
                onMapClick = onOpenMap,
                onStoryClick = onOpenStory,
                onProfileClick = onOpenProfile,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(resultBackgroundBrush()),
        ) {
            ResultBackdrop()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = 28.dp,
                    end = 24.dp,
                    bottom = 24.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    ResultHeader(
                        result = result,
                        isLoading = uiState.isLoading,
                    )
                }
                if (uiState.isAwaitingLocationWarning) {
                    item {
                        StatusPill(
                            text = UnverifiedLocationWarningMessage,
                            bold = true,
                            actionLabel = "그래도 만들기",
                            onAction = onContinueWithUnverifiedLocations,
                        )
                    }
                } else {
                    item {
                        ResultBookCard(result = result)
                    }
                }
                if (uiState.isLoading && result == null) {
                    item {
                        StatusPill(text = "완성본을 요청하는 중이에요.")
                    }
                }
                if (result != null && !canOpenEbook) {
                    item {
                        StatusPill(
                            text = result.toResultStatusMessage(),
                            actionLabel = if (result.status == EbookStatus.Processing) "다시 확인" else null,
                            onAction = onRetry,
                        )
                    }
                }
                if (uiState.errorMessage != null) {
                    item {
                        StatusPill(text = uiState.errorMessage)
                    }
                }
                if (!uiState.isAwaitingLocationWarning) {
                    item {
                        ResultActionButtons(
                            canOpenEbook = canOpenEbook,
                            canExportEbook = canExportEbook,
                            isExporting = uiState.isExporting,
                            onReadNow = {
                                val ebookId: String = result?.fileId.orEmpty()
                                if (ebookId.isNotBlank()) {
                                    onReadNow(ebookId)
                                }
                            },
                            onExport = onExport,
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun ResultBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(14) { index ->
            val x = ((index * 67 + 18) % 100) / 100f * size.width
            val y = ((index * 41 + 10) % 100) / 100f * size.height
            val radius = 4f + (index % 4) * 2f
            drawCircle(
                color = Color.White.copy(alpha = 0.42f),
                radius = radius,
                center = Offset(x, y),
            )
        }
        repeat(8) { index ->
            val x = ((index * 83 + 34) % 100) / 100f * size.width
            val y = ((index * 57 + 20) % 100) / 100f * size.height
            val length = 8f + (index % 2) * 4f
            drawLine(
                color = Color.White.copy(alpha = 0.68f),
                start = Offset(x - length, y),
                end = Offset(x + length, y),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.68f),
                start = Offset(x, y - length),
                end = Offset(x, y + length),
                strokeWidth = 2.4f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ResultHeader(
    result: EbookResult?,
    isLoading: Boolean,
) {
    val statusText: String = result.toHeaderStatusText(isLoading)
    val titleText: String = result.toHeaderTitleText(isLoading)
    val bodyText: String = result.toHeaderBodyText(isLoading)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Text(
            text = titleText,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = bodyText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultBookCard(
    result: EbookResult?,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val title: String = result?.title?.ifBlank { null } ?: "경주에서 만난 나의 이야기"
    val coverTitle: String = title.toCoverTitle()
    val caption: String = result?.metadata?.pageCount?.let { pageCount ->
        "${pageCount}쪽 완성본"
    } ?: "사진과 장소가 담긴 완성본"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isLandscape) 300.dp else 430.dp),
        contentAlignment = Alignment.Center,
    ) {
        SnapshotPhoto(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .rotate(7f),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 52.dp, start = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(34.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shadowElevation = 14.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 168.dp else 266.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(BookCoverBrush)
                        .border(
                            width = 1.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(28.dp),
                        ),
                ) {
                    CoverSparkles()
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(18.dp),
                        shape = CircleShape,
                        color = Color.White,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MenuBook,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(26.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Image(
                        painter = painterResource(id = R.drawable.atoria_mascot),
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp, top = 36.dp)
                            .size(154.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = coverTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "우리 가족 문화 여행 기록",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = Color(0xFFFFE4EE),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.padding(12.dp),
                            tint = Color(0xFFFFB800),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = caption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnapshotPhoto(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .width(128.dp)
            .height(156.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 9.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SnapshotBrush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "오늘의 한 장",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CoverSparkles() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        repeat(10) { index ->
            val x = ((index * 59 + 14) % 100) / 100f * size.width
            val y = ((index * 37 + 16) % 100) / 100f * size.height
            val length = 6f + (index % 3) * 3f
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(x - length, y),
                end = Offset(x + length, y),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.72f),
                start = Offset(x, y - length),
                end = Offset(x, y + length),
                strokeWidth = 2.2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ResultActionButtons(
    canOpenEbook: Boolean,
    canExportEbook: Boolean,
    isExporting: Boolean,
    onReadNow: () -> Unit,
    onExport: () -> Unit,
) {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val buttonHeight = if (isLandscape) 50.dp else 56.dp
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp),
    ) {
        Button(
            onClick = onReadNow,
            enabled = canOpenEbook,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "지금 읽어보기",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        OutlinedButton(
            onClick = onExport,
            enabled = canExportEbook && !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        Color(0xFFFFC845).copy(alpha = 0.74f),
                    ),
                ),
            ),
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Rounded.Share,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isExporting) "내보내는 중" else "eBook 내보내기",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private fun String.toCoverTitle(): String {
    val trimmedTitle = trim()
    if (trimmedTitle.length <= CoverTitleLineLength) return trimmedTitle
    val splitIndex = trimmedTitle
        .take(CoverTitleLineLength + 1)
        .lastIndexOf(' ')
        .takeIf { index -> index > 0 }
        ?: CoverTitleLineLength
    return trimmedTitle.substring(0, splitIndex).trimEnd() +
        "\n" +
        trimmedTitle.substring(splitIndex).trimStart()
}

private fun EbookResult?.canOpenEbook(): Boolean =
    this?.status == EbookStatus.Completed &&
        fileId.isNotBlank() &&
        (fileUrl.isNotBlank() || hasContentPages())

private fun EbookResult?.canExportEbook(): Boolean =
    this?.status == EbookStatus.Completed &&
        fileId.isNotBlank() &&
        fileUrl.isNotBlank()

private fun EbookResult.hasContentPages(): Boolean =
    metadata?.content?.pages?.isNotEmpty() == true

private fun EbookResult.hasFinishedGenerating(): Boolean =
    when (status) {
        EbookStatus.Completed -> fileUrl.isNotBlank() || hasContentPages()
        EbookStatus.Failed -> true
        EbookStatus.Processing -> false
    }

private fun EbookResult.toResultStatusMessage(): String =
    when (status) {
        EbookStatus.Processing -> "동화책을 제작하고 있어요. 잠시 후 다시 확인해 주세요."
        EbookStatus.Completed -> "완성 정보는 도착했지만 eBook 파일 주소가 아직 준비되지 않았어요."
        EbookStatus.Failed -> "동화책 제작에 실패했어요. 다시 시도해 주세요."
    }

private fun EbookResult?.toHeaderStatusText(isLoading: Boolean): String =
    when {
        isLoading && this == null -> "동화책 제작 요청 중"
        this?.status == EbookStatus.Completed && canOpenEbook() -> "동화책 제작 완료"
        this?.status == EbookStatus.Failed -> "동화책 제작 실패"
        else -> "동화책 제작 중"
    }

private fun EbookResult?.toHeaderTitleText(isLoading: Boolean): String =
    when {
        isLoading && this == null -> "이야기를 엮는 중"
        this?.status == EbookStatus.Completed && canOpenEbook() -> "나만의 동화책 완성"
        this?.status == EbookStatus.Failed -> "완성하지 못했어요"
        else -> "조금만 기다려 주세요"
    }

private fun EbookResult?.toHeaderBodyText(isLoading: Boolean): String =
    when {
        isLoading && this == null -> "오늘의 추억을 eBook으로 만들 준비를 하고 있어요."
        this?.status == EbookStatus.Completed && canOpenEbook() -> "오늘의 추억이 반짝이는 이야기로 묶였어요."
        this?.status == EbookStatus.Failed -> "다시 확인하거나 새로 생성해 주세요."
        else -> "사진과 장소 기록을 동화책 페이지로 정리하고 있어요."
    }

private const val CoverTitleLineLength = 8
private const val EbookPollMaxAttempts = 24
private const val EbookPollIntervalMillis = 2_500L
private const val PdfMimeType = "application/pdf"
private const val UnverifiedLocationWarningMessage =
    "일부 사진의 장소 인증이 완료되지 않았어요.\n동화책 내용이나 장소 기록이 실제 방문 위치와 다를 수 있어요. 그래도 만들까요?"
@Composable
private fun resultBackgroundBrush(): Brush =
    Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerLow,
            MaterialTheme.colorScheme.background,
        ),
    )
private val BookCoverBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFFFF6F9D),
        Color(0xFFFFB65C),
        Color(0xFFFFE58A),
    ),
)
private val SnapshotBrush = Brush.linearGradient(
    colors = listOf(
        Color(0xFF8BC6FF),
        Color(0xFFFFA5C8),
        Color(0xFFFFE08A),
    ),
)
