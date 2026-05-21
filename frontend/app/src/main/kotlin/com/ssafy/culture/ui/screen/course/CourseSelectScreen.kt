package com.ssafy.culture.ui.screen.course

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.Image
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import com.kakao.vectormap.route.RouteLineStylesSet
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.R
import com.ssafy.culture.data.course.DefaultCourseRepository
import com.ssafy.culture.data.repository.KakaoCourseRoute
import com.ssafy.culture.data.repository.KakaoDirectionsRepository
import com.ssafy.culture.data.repository.KakaoRoutePoint
import com.ssafy.culture.data.repository.KakaoRouteStop
import com.ssafy.culture.domain.model.CourseSelectionDraft
import com.ssafy.culture.domain.model.CourseSummary
import com.ssafy.culture.domain.model.PlaceDetail
import com.ssafy.culture.domain.model.PlaceSummary
import com.ssafy.culture.ui.component.InfoPill
import com.ssafy.culture.ui.motion.CultureMotion
import com.ssafy.culture.ui.motion.tossClickable
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CourseSelectUiState(
    val selectedMode: CourseSelectMode = CourseSelectMode.Distance,
    val selectedDistanceId: String = DistanceOptions.first().id,
    val selectedThemeId: String = RecommendationOptions.first().id,
    val distanceCandidateGroups: Map<String, List<CourseCandidate>> = emptyMap(),
    val recommendationCandidateGroups: Map<String, List<CourseCandidate>> = emptyMap(),
    val mapPins: List<CourseMapPin> = emptyList(),
    val selectedMapPin: CourseMapPin? = null,
    val selectedPlaceIds: List<Long> = emptyList(),
    val route: CourseSelectRouteInfo? = null,
    val isLoading: Boolean = true,
    val isMapDetailLoading: Boolean = false,
    val isRouteLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleCandidates: List<CourseCandidate>
        get() = when (selectedMode) {
            CourseSelectMode.Distance -> distanceCandidateGroups[selectedDistanceId].orEmpty()
            CourseSelectMode.Recommended -> recommendationCandidateGroups[selectedThemeId].orEmpty()
        }

    val selectedPlaces: List<CourseSelectedPlace>
        get() {
            val titlesByPlaceId: Map<Long, String> = allPlaceTitlesById
            val thumbnailUrlsByPlaceId: Map<Long, String?> = allPlaceThumbnailUrlsById
            return selectedPlaceIds.map { placeId ->
                val title: String = titlesByPlaceId[placeId] ?: "문화 장소"
                CourseSelectedPlace(
                    id = placeId,
                    title = title,
                    thumbnailUrl = thumbnailUrlsByPlaceId[placeId],
                    shortTitle = title.toShortTitle(defaultValue = "장소"),
                )
            }
        }

    val selectedCourseId: Long
        get() {
            val selectedPlaces = selectedPlaceIds
            val courseCandidates = recommendationCandidateGroups.values.flatten()
                .distinctBy { candidate -> candidate.id }
                .filter { candidate -> candidate.toCourseIdOrNull() != null }
            val fullMatch = courseCandidates.firstOrNull { candidate ->
                selectedPlaces.isNotEmpty() && selectedPlaces.all { placeId -> placeId in candidate.placeIds }
            }
            val firstPlaceId = selectedPlaces.firstOrNull()
            val partialMatch = courseCandidates.firstOrNull { candidate ->
                firstPlaceId != null && firstPlaceId in candidate.placeIds
            }
            return fullMatch?.toCourseIdOrNull()
                ?: partialMatch?.toCourseIdOrNull()
                ?: courseCandidates.firstNotNullOfOrNull { candidate -> candidate.toCourseIdOrNull() }
                ?: DefaultCourseId
        }

    val selectedPlaceCount: Int
        get() = selectedPlaceIds.size

    fun isCandidateSelected(candidate: CourseCandidate): Boolean =
        candidate.placeIds.isNotEmpty() && candidate.placeIds.all { placeId -> placeId in selectedPlaceIds }

    fun selectedPlaceCountFor(candidate: CourseCandidate): Int =
        candidate.placeIds.count { placeId -> placeId in selectedPlaceIds }

    fun findCandidate(candidateId: String): CourseCandidate? =
        allCandidates.firstOrNull { candidate -> candidate.id == candidateId }

    private val allCandidates: List<CourseCandidate>
        get() = (distanceCandidateGroups.values.flatten() + recommendationCandidateGroups.values.flatten())
            .distinctBy { candidate -> candidate.id }

    private val allPlaceTitlesById: Map<Long, String>
        get() = buildMap {
            mapPins.forEach { pin -> put(pin.placeId, pin.title) }
            allCandidates.forEach { candidate ->
                candidate.placeIds.zip(candidate.placeTitles).forEach { (placeId, title) ->
                    putIfAbsent(placeId, title)
                }
            }
        }

    private val allPlaceThumbnailUrlsById: Map<Long, String?>
        get() = buildMap {
            mapPins.forEach { pin -> put(pin.placeId, pin.thumbnailUrl) }
            allCandidates.forEach { candidate ->
                if (candidate.placeIds.size == 1) {
                    putIfAbsent(candidate.placeIds.first(), candidate.thumbnailUrl)
                }
            }
        }
}

@HiltViewModel
class CourseSelectViewModel @Inject constructor(
    private val courseRepository: DefaultCourseRepository,
    private val directionsRepository: KakaoDirectionsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CourseSelectUiState())
    val uiState: StateFlow<CourseSelectUiState> = _uiState.asStateFlow()
    private var routeJob: Job? = null

    init {
        loadCandidates()
    }

    fun selectMode(optionId: String) {
        _uiState.update { state ->
            state.copy(
                selectedMode = if (optionId == CourseSelectMode.Recommended.id) {
                    CourseSelectMode.Recommended
                } else {
                    CourseSelectMode.Distance
                },
            )
        }
    }

    fun selectFilter(optionId: String) {
        _uiState.update { state ->
            if (state.selectedMode == CourseSelectMode.Distance) {
                state.copy(selectedDistanceId = optionId)
            } else {
                state.copy(selectedThemeId = optionId)
            }
        }
    }

    fun toggleCandidate(candidateId: String) {
        var updatedSelectedPlaceIds: List<Long>? = null
        _uiState.update { state ->
            val candidate: CourseCandidate = state.findCandidate(candidateId) ?: return@update state
            val candidatePlaceIds: List<Long> = candidate.placeIds
            if (candidatePlaceIds.isEmpty()) return@update state
            val selectedPlaceIds: List<Long> = state.selectedPlaceIds
            val isSelected: Boolean = candidatePlaceIds.all { placeId -> placeId in selectedPlaceIds }
            val updatedPlaceIds = if (isSelected) {
                selectedPlaceIds.filterNot { placeId -> placeId in candidatePlaceIds }
            } else {
                (selectedPlaceIds + candidatePlaceIds).distinct()
            }
            if (!isSelected && updatedPlaceIds.size > MaxSelectedPlaceCount) {
                state
            } else {
                updatedSelectedPlaceIds = updatedPlaceIds
                state.copy(selectedPlaceIds = updatedPlaceIds)
            }
        }
        updatedSelectedPlaceIds?.let(::loadSelectedCourseRoute)
    }

    fun togglePlace(placeId: Long) {
        var updatedSelectedPlaceIds: List<Long>? = null
        _uiState.update { state ->
            val selectedPlaceIds: List<Long> = state.selectedPlaceIds
            val updatedPlaceIds = when {
                placeId in selectedPlaceIds -> selectedPlaceIds - placeId
                selectedPlaceIds.size >= MaxSelectedPlaceCount -> selectedPlaceIds
                else -> selectedPlaceIds + placeId
            }
            updatedSelectedPlaceIds = updatedPlaceIds
            state.copy(selectedPlaceIds = updatedPlaceIds)
        }
        updatedSelectedPlaceIds?.let(::loadSelectedCourseRoute)
    }

    fun removePlace(placeId: Long) {
        var updatedSelectedPlaceIds: List<Long>? = null
        _uiState.update { state ->
            val updatedPlaceIds: List<Long> = state.selectedPlaceIds - placeId
            updatedSelectedPlaceIds = updatedPlaceIds
            state.copy(selectedPlaceIds = updatedPlaceIds)
        }
        updatedSelectedPlaceIds?.let(::loadSelectedCourseRoute)
    }

    fun confirmSelection(onConfirmed: (Long) -> Unit) {
        val state: CourseSelectUiState = _uiState.value
        if (state.selectedPlaceIds.isEmpty()) return
        val courseId: Long = state.selectedCourseId
        courseRepository.saveCourseSelectionDraft(
            CourseSelectionDraft(
                courseId = courseId,
                placeIds = state.selectedPlaceIds,
            ),
        )
        onConfirmed(courseId)
    }

    fun selectMapPin(placeId: Long) {
        val fallbackPin: CourseMapPin = _uiState.value.mapPins.firstOrNull { pin ->
            pin.placeId == placeId
        } ?: return
        _uiState.update { state ->
            state.copy(
                selectedMapPin = fallbackPin,
                isMapDetailLoading = true,
            )
        }
        viewModelScope.launch {
            runCatching {
                courseRepository.getPlaceDetail(placeId)
            }.onSuccess { detail ->
                val detailPin = detail.toCourseMapPin(fallbackPin = fallbackPin)
                _uiState.update { state ->
                    state.copy(
                        mapPins = state.mapPins.map { pin ->
                            if (pin.placeId == placeId) detailPin else pin
                        },
                        selectedMapPin = detailPin,
                        isMapDetailLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(isMapDetailLoading = false)
                }
            }
        }
    }

    fun clearMapPinSelection() {
        _uiState.update { state ->
            state.copy(
                selectedMapPin = null,
                isMapDetailLoading = false,
            )
        }
    }

    private fun loadCandidates() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val courses = courseRepository.getCourses()
                val places = loadPlaces()
                CourseSelectLoadResult(
                    candidates = buildCourseCandidates(courses = courses, places = places),
                    mapPins = places.mapIndexedNotNull { index, place ->
                        place.toCourseMapPin(order = index + 1)
                    },
                )
            }.onSuccess { candidates ->
                _uiState.update { state ->
                    state.copy(
                        distanceCandidateGroups = candidates.candidates.distanceGroups,
                        recommendationCandidateGroups = candidates.candidates.recommendationGroups,
                        mapPins = candidates.mapPins,
                        isLoading = false,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "코스 정보를 불러오지 못했어요.",
                    )
                }
            }
        }
    }

    private suspend fun loadPlaces(): List<PlaceSummary> {
        val firstPage = courseRepository.getPlaces(
            page = FirstCoursePlacePage,
            size = CoursePlacePageSize,
        )
        val totalPages: Int = firstPage.pageInfo?.totalPages ?: 1
        if (totalPages <= 1) return firstPage.places
        val remainingPlaces: List<PlaceSummary> = (1 until totalPages).flatMap { page ->
            courseRepository.getPlaces(
                page = page,
                size = CoursePlacePageSize,
            ).places
        }
        return (firstPage.places + remainingPlaces).distinctBy(PlaceSummary::id)
    }

    private fun loadSelectedCourseRoute(
        selectedPlaceIds: List<Long>,
    ) {
        routeJob?.cancel()
        val routeStops: List<KakaoRouteStop> = _uiState.value.mapPins.toRouteStops(selectedPlaceIds)
        if (routeStops.size < MinimumRouteStopCount) {
            _uiState.update { state ->
                state.copy(route = null, isRouteLoading = false)
            }
            return
        }
        _uiState.update { state ->
            state.copy(route = null, isRouteLoading = true)
        }
        routeJob = viewModelScope.launch {
            runCatching {
                directionsRepository.getCourseRoute(routeStops)
            }.onSuccess { route ->
                _uiState.update { state ->
                    if (state.selectedPlaceIds == selectedPlaceIds) {
                        state.copy(route = route?.toSelectRouteInfo(), isRouteLoading = false)
                    } else {
                        state
                    }
                }
            }.onFailure {
                _uiState.update { state ->
                    if (state.selectedPlaceIds == selectedPlaceIds) {
                        state.copy(route = null, isRouteLoading = false)
                    } else {
                        state
                    }
                }
            }
        }
    }
}

@Composable
fun CourseSelectRoute(
    onBack: () -> Unit,
    onNext: (Long) -> Unit,
    viewModel: CourseSelectViewModel = hiltViewModel(),
): Unit {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CourseSelectScreen(
        uiState = uiState,
        onBack = onBack,
        onNext = { viewModel.confirmSelection(onNext) },
        onModeSelected = viewModel::selectMode,
        onFilterSelected = viewModel::selectFilter,
        onToggleCandidate = viewModel::toggleCandidate,
        onTogglePlace = viewModel::togglePlace,
        onRemovePlace = viewModel::removePlace,
        onMapPinSelected = viewModel::selectMapPin,
        onMapPinClosed = viewModel::clearMapPinSelection,
    )
}

@Composable
fun CourseSelectScreen(
    uiState: CourseSelectUiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onModeSelected: (String) -> Unit,
    onFilterSelected: (String) -> Unit,
    onToggleCandidate: (String) -> Unit,
    onTogglePlace: (Long) -> Unit,
    onRemovePlace: (Long) -> Unit,
    onMapPinSelected: (Long) -> Unit,
    onMapPinClosed: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val visibleCandidates: List<CourseCandidate> = uiState.visibleCandidates
    val selectedPlaces: List<CourseSelectedPlace> = uiState.selectedPlaces
    var isMapGestureInProgress by remember { mutableStateOf(false) }
    var mapGestureTick by remember { mutableStateOf(0) }
    val updateMapGestureInProgress: (Boolean) -> Unit = { isInProgress ->
        isMapGestureInProgress = isInProgress
        if (isInProgress) {
            mapGestureTick += 1
        }
    }
    LaunchedEffect(mapGestureTick) {
        if (mapGestureTick > 0) {
            delay(MapGestureScrollLockTimeoutMillis)
            isMapGestureInProgress = false
        }
    }
    val mapScrollLockConnection = remember(isMapGestureInProgress) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset =
                if (isMapGestureInProgress && source == NestedScrollSource.UserInput) {
                    available
                } else {
                    Offset.Zero
                }

            override suspend fun onPreFling(available: Velocity): Velocity =
                if (isMapGestureInProgress) {
                    available
                } else {
                    Velocity.Zero
                }
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent,
        bottomBar = {
            SelectionBasket(
                selectedPlaces = selectedPlaces,
                onRemove = onRemovePlace,
                onNext = onNext,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CourseBackgroundBrush),
        ) {
            CourseBackdrop()
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(mapScrollLockConnection)
                    .padding(innerPadding),
                userScrollEnabled = !isMapGestureInProgress,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 18.dp,
                    end = 20.dp,
                    bottom = 22.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    CourseSelectHeader(onBack = onBack)
                }
                stickyHeader {
                    CourseHeritageMap(
                        mapPins = uiState.mapPins,
                        selectedPin = uiState.selectedMapPin,
                        selectedPlaceIds = uiState.selectedPlaceIds,
                        route = uiState.route,
                        isLoading = uiState.isLoading,
                        isDetailLoading = uiState.isMapDetailLoading,
                        isRouteLoading = uiState.isRouteLoading,
                        onPinSelected = onMapPinSelected,
                        onPinClosed = onMapPinClosed,
                        onTogglePin = { pin -> onTogglePlace(pin.placeId) },
                        onGestureInProgressChanged = updateMapGestureInProgress,
                    )
                }
                item {
                    CourseSectionTitle(
                        title = "코스 고르기",
                        subtitle = "오늘의 이동 거리와 분위기에 맞춰 담아보세요",
                    )
                }
                item {
                    SegmentedControl(
                        options = ModeOptions,
                        selectedOptionId = uiState.selectedMode.id,
                        onOptionSelected = onModeSelected,
                    )
                }
                item {
                    val options: List<SegmentOption> = if (uiState.selectedMode == CourseSelectMode.Distance) {
                        DistanceOptions
                    } else {
                        RecommendationOptions
                    }
                    val selectedOptionId: String = if (uiState.selectedMode == CourseSelectMode.Distance) {
                        uiState.selectedDistanceId
                    } else {
                        uiState.selectedThemeId
                    }
                    SegmentedControl(
                        options = options,
                        selectedOptionId = selectedOptionId,
                        onOptionSelected = onFilterSelected,
                    )
                }
                item {
                    CourseSectionTitle(
                        title = "장소/코스 카드",
                        subtitle = "카드를 눌러 선택 바구니에 추가하거나 빼세요",
                    )
                }
                if (uiState.isLoading || uiState.errorMessage != null) {
                    item {
                        CourseStatusMessage(
                            text = uiState.errorMessage ?: "코스를 불러오는 중이에요",
                        )
                    }
                }
                items(
                    items = visibleCandidates,
                    key = { candidate -> candidate.id },
                ) { candidate ->
                    CourseCandidateCard(
                        candidate = candidate,
                        isSelected = uiState.isCandidateSelected(candidate),
                        selectedPlaceCount = uiState.selectedPlaceCountFor(candidate),
                        onClick = { onToggleCandidate(candidate.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseSelectHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "문화 산책",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "코스 선택",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "추천",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}

@Composable
private fun CourseHeritageMap(
    mapPins: List<CourseMapPin>,
    selectedPin: CourseMapPin?,
    selectedPlaceIds: List<Long>,
    route: CourseSelectRouteInfo?,
    isLoading: Boolean,
    isDetailLoading: Boolean,
    isRouteLoading: Boolean,
    onPinSelected: (Long) -> Unit,
    onPinClosed: () -> Unit,
    onTogglePin: (CourseMapPin) -> Unit,
    onGestureInProgressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CourseMapHeight)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
                CourseFallbackMap(
                    mapPins = mapPins,
                    selectedPinId = selectedPin?.placeId,
                    onPinSelected = onPinSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CourseKakaoMapViewport(
                    mapPins = mapPins,
                    selectedPin = selectedPin,
                    selectedPlaceIds = selectedPlaceIds,
                    route = route,
                    onPinSelected = onPinSelected,
                    onMapTapped = onPinClosed,
                    onGestureInProgressChanged = onGestureInProgressChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            CourseMapTitlePill(
                count = mapPins.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            )
            CourseMapRoutePill(
                route = route,
                isLoading = isRouteLoading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            )
            if (isLoading) {
                CourseMapLoadingPill(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            selectedPin?.let { pin ->
                CoursePlaceBottomSheet(
                    pin = pin,
                    isSelected = pin.placeId in selectedPlaceIds,
                    isLoading = isDetailLoading,
                    onClose = onPinClosed,
                    onToggle = { onTogglePin(pin) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun CourseKakaoMapViewport(
    mapPins: List<CourseMapPin>,
    selectedPin: CourseMapPin?,
    selectedPlaceIds: List<Long>,
    route: CourseSelectRouteInfo?,
    onPinSelected: (Long) -> Unit,
    onMapTapped: () -> Unit,
    onGestureInProgressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val fitPaddingPx: Int = with(LocalDensity.current) {
        CourseMapFitPadding.toPx().roundToInt()
    }
    val markerResourceBitmaps: Map<Int, Bitmap> = remember(context) {
        CoursePlaceMarkerThumbnailResourceIds.values
            .distinct()
            .mapNotNull { resourceId ->
                BitmapFactory.decodeResource(context.resources, resourceId)?.let { bitmap ->
                    resourceId to bitmap
                }
            }
            .toMap()
    }
    var markerThumbnailBitmaps: Map<Long, Bitmap> by remember { mutableStateOf(emptyMap()) }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val latestOnPinSelected by rememberUpdatedState(onPinSelected)
    val latestOnMapTapped by rememberUpdatedState(onMapTapped)
    val latestOnGestureInProgressChanged by rememberUpdatedState(onGestureInProgressChanged)
    val mapView = remember {
        MapView(context).also { view ->
            view.setOnTouchListener { touchedView, event ->
                handleCourseMapTouchEvent(
                    event = event,
                    onGestureInProgressChanged = latestOnGestureInProgressChanged,
                    onParentInterceptChanged = touchedView.parent::requestDisallowInterceptTouchEvent,
                )
                false
            }
            view.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit
                    override fun onMapError(error: Exception?) = Unit
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(readyMap: KakaoMap) {
                        readyMap.setOnMapClickListener { _, _, _, _ ->
                            latestOnMapTapped()
                        }
                        readyMap.setOnLabelClickListener { _, _, label ->
                            val pin = label.tag as? CourseMapPin
                            if (pin != null) {
                                latestOnPinSelected(pin.placeId)
                            }
                            pin != null
                        }
                        kakaoMap = readyMap
                    }

                    override fun getPosition(): LatLng =
                        mapPins.firstOrNull()?.toLatLng()
                            ?: LatLng.from(DefaultCourseMapLatitude, DefaultCourseMapLongitude)

                    override fun getZoomLevel(): Int = CourseMapZoomLevel
                },
            )
        }
    }
    LaunchedEffect(context, mapPins, markerResourceBitmaps) {
        markerThumbnailBitmaps = loadCourseMapMarkerThumbnailBitmaps(
            context = context,
            mapPins = mapPins,
            markerResourceBitmaps = markerResourceBitmaps,
        )
    }
    LaunchedEffect(kakaoMap, mapPins, selectedPin?.placeId, selectedPlaceIds, route, fitPaddingPx, markerThumbnailBitmaps) {
        kakaoMap?.let { readyMap ->
            renderCourseMapRouteLine(
                kakaoMap = readyMap,
                route = route,
            )
            renderCourseMapPins(
                kakaoMap = readyMap,
                mapPins = mapPins,
                selectedPinId = selectedPin?.placeId,
                markerThumbnailBitmaps = markerThumbnailBitmaps,
            )
            selectedPin?.let { pin ->
                readyMap.moveCamera(
                    CameraUpdateFactory.newCenterPosition(
                        pin.toLatLng(),
                        CourseSelectedMapZoomLevel,
                    ),
                )
            } ?: moveCourseMapCamera(
                kakaoMap = readyMap,
                mapPins = mapPins,
                selectedPlaceIds = selectedPlaceIds,
                fitPaddingPx = fitPaddingPx,
            )
        }
    }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { mapView.resume() }
                Lifecycle.Event.ON_PAUSE -> runCatching { mapView.pause() }
                Lifecycle.Event.ON_DESTROY -> runCatching { mapView.finish() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            runCatching { mapView.resume() }
        }
        onDispose {
            latestOnGestureInProgressChanged(false)
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.pause() }
            runCatching { mapView.finish() }
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInteropFilter { event ->
                    handleCourseMapTouchEvent(
                        event = event,
                        onGestureInProgressChanged = latestOnGestureInProgressChanged,
                    )
                    false
                },
            factory = { mapView },
        )
        CourseMapZoomControls(
            enabled = kakaoMap != null,
            onZoomIn = {
                kakaoMap?.moveCamera(CameraUpdateFactory.zoomIn())
            },
            onZoomOut = {
                kakaoMap?.moveCamera(CameraUpdateFactory.zoomOut())
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        )
    }
}

private fun handleCourseMapTouchEvent(
    event: MotionEvent,
    onGestureInProgressChanged: (Boolean) -> Unit,
    onParentInterceptChanged: (Boolean) -> Unit = {},
) {
    val isGestureInProgress = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN,
        MotionEvent.ACTION_POINTER_DOWN,
        MotionEvent.ACTION_MOVE,
            -> true

        MotionEvent.ACTION_POINTER_UP,
            -> event.pointerCount > 1

        MotionEvent.ACTION_UP,
        MotionEvent.ACTION_CANCEL,
            -> false

        else -> return
    }
    onGestureInProgressChanged(isGestureInProgress)
    onParentInterceptChanged(isGestureInProgress)
}

@Composable
private fun CourseMapZoomControls(
    enabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CourseMapZoomButton(
                enabled = enabled,
                icon = Icons.Rounded.Add,
                contentDescription = "지도 확대",
                onClick = onZoomIn,
            )
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(1.dp)
                    .background(Color(0xFFE9E0E4)),
            )
            CourseMapZoomButton(
                enabled = enabled,
                icon = Icons.Rounded.Remove,
                contentDescription = "지도 축소",
                onClick = onZoomOut,
            )
        }
    }
}

@Composable
private fun CourseMapZoomButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
): Unit {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(42.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onBackground else Color(0xFFCFC4CA),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CourseFallbackMap(
    mapPins: List<CourseMapPin>,
    selectedPinId: Long?,
    onPinSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Box(
        modifier = modifier.background(Color(0xFFE3E0DA)),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(18) { index ->
                val y = size.height * (index / 18f)
                drawLine(
                    color = Color.White.copy(alpha = 0.34f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y + size.height * 0.15f),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
            }
            repeat(10) { index ->
                val x = size.width * (index / 10f)
                drawLine(
                    color = Color.White.copy(alpha = 0.28f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width * 0.22f, size.height),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round,
                )
            }
        }
        mapPins.take(CourseFallbackPinPositions.size).forEachIndexed { index, pin ->
            val position = CourseFallbackPinPositions[index]
            CourseFloatingMapPin(
                pin = pin,
                isSelected = pin.placeId == selectedPinId,
                onClick = { onPinSelected(pin.placeId) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = position.xOffset, y = position.yOffset),
            )
        }
    }
}

@Composable
private fun CourseFloatingMapPin(
    pin: CourseMapPin,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .tossClickable(
                role = Role.Button,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color.White),
        shadowElevation = if (isSelected) 10.dp else 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CoursePinNumber(order = pin.order, size = 18.dp)
                Text(
                    text = pin.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CoursePlaceThumbnail(
                placeId = pin.placeId,
                title = pin.title,
                thumbnailUrl = pin.thumbnailUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            )
        }
    }
}

@Composable
private fun CourseMapTitlePill(
    count: Int,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "문화재 ${count}곳",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun CourseMapRoutePill(
    route: CourseSelectRouteInfo?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
): Unit {
    val text: String = when {
        isLoading -> "경로 계산 중"
        route != null -> "차량 ${route.durationLabel} · ${route.distanceLabel}"
        else -> return
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CourseMapLoadingPill(
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 7.dp,
    ) {
        Text(
            text = "코스 지도를 준비하고 있어요",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoursePlaceBottomSheet(
    pin: CourseMapPin,
    isSelected: Boolean,
    isLoading: Boolean,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color.White,
        shadowElevation = 16.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, end = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "닫기",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(role = Role.Button, onClick = onToggle),
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color(0xFFE5E5E5),
                    border = BorderStroke(
                        width = if (isSelected) 1.dp else 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color(0xFF9E9E9E),
                    ),
                    shadowElevation = 3.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "선택됨",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoursePlaceThumbnail(
                    placeId = pin.placeId,
                    title = pin.title,
                    thumbnailUrl = pin.thumbnailUrl,
                    modifier = Modifier.size(width = 84.dp, height = 92.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = pin.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = pin.distanceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4E6CFF),
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = if (isLoading) {
                            "상세 정보를 불러오는 중이에요."
                        } else {
                            pin.description
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CoursePinNumber(
    order: Int,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
): Unit {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFD9D9D9)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = order.toString(),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Black),
            color = Color.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun CoursePlaceThumbnail(
    placeId: Long,
    title: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
): Unit {
    val resolvedThumbnailModel = remember(placeId, title, thumbnailUrl) {
        resolveCoursePlaceThumbnailModel(
            placeId = placeId,
            title = title,
            thumbnailUrl = thumbnailUrl,
        )
    }
    AsyncImage(
        model = resolvedThumbnailModel,
        contentDescription = null,
        placeholder = painterResource(id = R.drawable.login_hero_scene),
        error = painterResource(id = R.drawable.login_hero_scene),
        contentScale = ContentScale.Crop,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFE8F2FF)),
    )
}

private fun renderCourseMapPins(
    kakaoMap: KakaoMap,
    mapPins: List<CourseMapPin>,
    selectedPinId: Long?,
    markerThumbnailBitmaps: Map<Long, Bitmap>,
) {
    val labelManager = kakaoMap.labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    layer.setClickable(true)
    mapPins.forEachIndexed { index, pin ->
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(
                    createCoursePinBitmap(
                        pin = pin,
                        isSelected = pin.placeId == selectedPinId,
                        thumbnailBitmap = markerThumbnailBitmaps[pin.placeId],
                    ),
                ).setAnchorPoint(0.5f, 1f),
            ),
        )
        layer.addLabel(
            LabelOptions.from(
                "course-place-${pin.placeId}",
                pin.toLatLng(),
            )
                .setStyles(styles)
                .setClickable(true)
                .setTag(pin)
                .setRank(index.toLong()),
        )
    }
}

private fun renderCourseMapRouteLine(
    kakaoMap: KakaoMap,
    route: CourseSelectRouteInfo?,
) {
    val layer = kakaoMap.routeLineManager?.layer ?: return
    layer.removeAll()
    val points: List<LatLng> = route?.points
        .orEmpty()
        .map(CourseSelectRoutePoint::toLatLng)
    if (points.size < MinimumRouteStopCount) return
    val styles = RouteLineStyles.from(
        RouteLineStyle.from(
            CourseMapRouteLineWidth,
            AndroidColor.rgb(255, 207, 84),
            CourseMapRouteStrokeWidth,
            AndroidColor.WHITE,
        ),
    )
    val stylesSet = RouteLineStylesSet.from(CourseMapRouteStyleId, styles)
    val segment = RouteLineSegment.from(points, stylesSet.getStyles(0))
    val options = RouteLineOptions
        .from(CourseMapRouteLineId, segment)
        .setStylesSet(stylesSet)
        .setZOrder(CourseMapRouteLineZOrder)
    layer.addRouteLine(options)
}

private fun moveCourseMapCamera(
    kakaoMap: KakaoMap,
    mapPins: List<CourseMapPin>,
    selectedPlaceIds: List<Long>,
    fitPaddingPx: Int,
) {
    val selectedPins: List<CourseMapPin> = mapPins.orderedByPlaceIds(selectedPlaceIds)
    when {
        selectedPins.size >= MinimumRouteStopCount -> kakaoMap.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                selectedPins.map(CourseMapPin::toLatLng).toTypedArray(),
                fitPaddingPx,
            ),
        )

        selectedPins.size == 1 -> kakaoMap.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                selectedPins.first().toLatLng(),
                CourseSelectedMapZoomLevel,
            ),
        )

        else -> mapPins.firstOrNull()?.let { firstPin ->
            kakaoMap.moveCamera(
                CameraUpdateFactory.newCenterPosition(
                    firstPin.toLatLng(),
                    CourseMapZoomLevel,
                ),
            )
        }
    }
}

private fun createCoursePinBitmap(
    pin: CourseMapPin,
    isSelected: Boolean,
    thumbnailBitmap: Bitmap?,
): Bitmap {
    val bitmap = Bitmap.createBitmap(CoursePinBitmapWidth, CoursePinBitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val card = RectF(7f, 18f, CoursePinBitmapWidth - 7f, CoursePinBitmapHeight - 12f)
    paint.color = AndroidColor.argb(255, 255, 255, 255)
    canvas.drawRoundRect(card, 20f, 20f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = if (isSelected) 5f else 2f
    paint.color = if (isSelected) AndroidColor.rgb(255, 207, 84) else AndroidColor.argb(120, 255, 255, 255)
    canvas.drawRoundRect(card, 20f, 20f, paint)
    paint.style = Paint.Style.FILL
    paint.color = AndroidColor.rgb(217, 217, 217)
    canvas.drawCircle(30f, 36f, 16f, paint)
    paint.color = AndroidColor.BLACK
    paint.textSize = 19f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(pin.order.toString(), 30f, 43f, paint)
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 18f
    paint.color = AndroidColor.rgb(35, 23, 29)
    canvas.drawText(pin.title.toPinTitle(), 54f, 43f, paint)
    drawCourseThumbnail(canvas = canvas, paint = paint, pin = pin, thumbnailBitmap = thumbnailBitmap)
    if (isSelected) {
        paint.color = AndroidColor.rgb(233, 74, 123)
        canvas.drawCircle(CoursePinBitmapWidth - 18f, 18f, 9f, paint)
    }
    return bitmap
}

private fun drawCourseThumbnail(
    canvas: AndroidCanvas,
    paint: Paint,
    pin: CourseMapPin,
    thumbnailBitmap: Bitmap?,
) {
    val image = RectF(28f, 58f, CoursePinBitmapWidth - 28f, CoursePinBitmapHeight - 22f)
    if (thumbnailBitmap != null) {
        drawCourseThumbnailImage(canvas = canvas, paint = paint, image = image, bitmap = thumbnailBitmap)
        return
    }
    paint.style = Paint.Style.FILL
    when (pin.toThumbnailKind()) {
        CoursePinThumbnailKind.Cheomseongdae -> drawCheomseongdaeThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Seokbinggo -> drawSeokbinggoThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.PalacePond -> drawPalacePondThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Temple -> drawTempleThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Grotto -> drawGrottoThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Tombs -> drawTombsThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Bridge -> drawBridgeThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Museum -> drawMuseumThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Village -> drawVillageThumbnail(canvas, paint, image)
        CoursePinThumbnailKind.Street -> drawStreetThumbnail(canvas, paint, image)
    }
}

private suspend fun loadCourseMapMarkerThumbnailBitmaps(
    context: Context,
    mapPins: List<CourseMapPin>,
    markerResourceBitmaps: Map<Int, Bitmap>,
): Map<Long, Bitmap> =
    buildMap {
        mapPins.forEach { pin ->
            val resourceBitmap = pin.resolveCourseMapMarkerThumbnailResourceId()
                ?.let(markerResourceBitmaps::get)
            val bitmap = resourceBitmap ?: loadCourseMapMarkerThumbnailBitmap(
                context = context,
                pin = pin,
            )
            if (bitmap != null) {
                put(pin.placeId, bitmap)
            }
        }
    }

private suspend fun loadCourseMapMarkerThumbnailBitmap(
    context: Context,
    pin: CourseMapPin,
): Bitmap? {
    val thumbnailUrl = resolveCoursePlaceThumbnailUrl(
        placeId = pin.placeId,
        title = pin.title,
        thumbnailUrl = pin.thumbnailUrl,
    )?.takeUnless { url ->
        url.contains("cdn-url", ignoreCase = true)
    } ?: return null
    val request = ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .allowHardware(false)
        .size(CourseMapMarkerThumbnailDecodeSize)
        .build()
    val result = context.imageLoader.execute(request)
    return (result as? SuccessResult)?.image?.toCourseMapMarkerBitmap()
}

private fun Image.toCourseMapMarkerBitmap(): Bitmap {
    val bitmapWidth = width.takeIf { it > 0 } ?: CourseMapMarkerThumbnailDecodeSize
    val bitmapHeight = height.takeIf { it > 0 } ?: CourseMapMarkerThumbnailDecodeSize
    return toBitmap(width = bitmapWidth, height = bitmapHeight)
}

private fun drawCourseThumbnailImage(
    canvas: AndroidCanvas,
    paint: Paint,
    image: RectF,
    bitmap: Bitmap,
) {
    val clipPath = android.graphics.Path().apply {
        addRoundRect(image, 10f, 10f, android.graphics.Path.Direction.CW)
    }
    val checkpoint = canvas.save()
    canvas.clipPath(clipPath)
    val source = bitmap.toCenterCropSourceRect(
        targetWidth = image.width(),
        targetHeight = image.height(),
    )
    val wasFilterBitmap = paint.isFilterBitmap
    paint.style = Paint.Style.FILL
    paint.isFilterBitmap = true
    canvas.drawBitmap(bitmap, source, image, paint)
    paint.isFilterBitmap = wasFilterBitmap
    canvas.restoreToCount(checkpoint)
}

private fun Bitmap.toCenterCropSourceRect(
    targetWidth: Float,
    targetHeight: Float,
): Rect {
    val targetAspectRatio = targetWidth / targetHeight
    val bitmapAspectRatio = width.toFloat() / height.toFloat()
    return if (bitmapAspectRatio > targetAspectRatio) {
        val cropWidth = (height * targetAspectRatio).roundToInt().coerceAtMost(width)
        val left = (width - cropWidth) / 2
        Rect(left, 0, left + cropWidth, height)
    } else {
        val cropHeight = (width / targetAspectRatio).roundToInt().coerceAtMost(height)
        val top = (height - cropHeight) / 2
        Rect(0, top, width, top + cropHeight)
    }
}

private fun drawCheomseongdaeThumbnail(
    canvas: AndroidCanvas,
    paint: Paint,
    image: RectF,
) {
    paint.color = AndroidColor.rgb(186, 220, 255)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(236, 246, 255)
    canvas.drawRect(image.left, image.top, image.right, image.top + 30f, paint)
    paint.color = AndroidColor.rgb(117, 102, 63)
    canvas.drawRect(image.left, image.bottom - 18f, image.right, image.bottom, paint)
    paint.color = AndroidColor.rgb(204, 184, 103)
    val tower = android.graphics.Path().apply {
        moveTo(image.centerX() - 16f, image.bottom - 18f)
        lineTo(image.centerX() - 8f, image.top + 22f)
        lineTo(image.centerX() + 8f, image.top + 22f)
        lineTo(image.centerX() + 16f, image.bottom - 18f)
        close()
    }
    canvas.drawPath(tower, paint)
    paint.color = AndroidColor.rgb(72, 61, 41)
    canvas.drawRect(image.centerX() - 7f, image.top + 42f, image.centerX() + 7f, image.top + 49f, paint)
}

private fun drawSeokbinggoThumbnail(
    canvas: AndroidCanvas,
    paint: Paint,
    image: RectF,
) {
    paint.color = AndroidColor.rgb(211, 229, 238)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(236, 244, 247)
    canvas.drawRect(image.left, image.top, image.right, image.top + 34f, paint)
    paint.color = AndroidColor.rgb(124, 135, 132)
    canvas.drawOval(
        RectF(image.left + 15f, image.top + 36f, image.right - 15f, image.bottom + 22f),
        paint,
    )
    paint.color = AndroidColor.rgb(91, 101, 100)
    canvas.drawOval(
        RectF(image.left + 28f, image.top + 52f, image.right - 28f, image.bottom + 12f),
        paint,
    )
    paint.color = AndroidColor.rgb(236, 246, 247)
    canvas.drawRect(image.left, image.bottom - 16f, image.right, image.bottom, paint)
}

private fun drawPalacePondThumbnail(
    canvas: AndroidCanvas,
    paint: Paint,
    image: RectF,
) {
    paint.color = AndroidColor.rgb(169, 211, 227)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(72, 132, 162)
    canvas.drawRect(image.left, image.bottom - 24f, image.right, image.bottom, paint)
    paint.color = AndroidColor.rgb(189, 77, 67)
    canvas.drawRect(image.left + 20f, image.top + 36f, image.right - 20f, image.top + 55f, paint)
    paint.color = AndroidColor.rgb(105, 50, 44)
    val roof = android.graphics.Path().apply {
        moveTo(image.left + 14f, image.top + 38f)
        lineTo(image.centerX(), image.top + 19f)
        lineTo(image.right - 14f, image.top + 38f)
        close()
    }
    canvas.drawPath(roof, paint)
    paint.color = AndroidColor.rgb(249, 213, 111)
    canvas.drawRect(image.left + 28f, image.top + 55f, image.right - 28f, image.bottom - 24f, paint)
}

private fun drawTempleThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(218, 231, 216)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(99, 63, 54)
    val roof = android.graphics.Path().apply {
        moveTo(image.left + 10f, image.top + 38f)
        lineTo(image.centerX(), image.top + 14f)
        lineTo(image.right - 10f, image.top + 38f)
        close()
    }
    canvas.drawPath(roof, paint)
    paint.color = AndroidColor.rgb(193, 81, 63)
    canvas.drawRect(image.left + 20f, image.top + 38f, image.right - 20f, image.bottom - 18f, paint)
    paint.color = AndroidColor.rgb(105, 122, 82)
    canvas.drawRect(image.left, image.bottom - 18f, image.right, image.bottom, paint)
}

private fun drawGrottoThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(221, 231, 211)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(117, 139, 99)
    canvas.drawOval(RectF(image.left + 8f, image.top + 26f, image.right - 8f, image.bottom + 22f), paint)
    paint.color = AndroidColor.rgb(84, 79, 73)
    canvas.drawOval(RectF(image.left + 31f, image.top + 52f, image.right - 31f, image.bottom + 8f), paint)
}

private fun drawTombsThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(201, 226, 200)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(95, 157, 91)
    canvas.drawOval(RectF(image.left + 5f, image.top + 38f, image.centerX() + 12f, image.bottom + 22f), paint)
    paint.color = AndroidColor.rgb(76, 138, 79)
    canvas.drawOval(RectF(image.centerX() - 8f, image.top + 30f, image.right - 5f, image.bottom + 20f), paint)
    paint.color = AndroidColor.rgb(230, 214, 150)
    canvas.drawRect(image.left, image.bottom - 14f, image.right, image.bottom, paint)
}

private fun drawBridgeThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(184, 217, 237)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(54, 125, 166)
    canvas.drawRect(image.left, image.bottom - 22f, image.right, image.bottom, paint)
    paint.color = AndroidColor.rgb(164, 65, 54)
    canvas.drawRect(image.left + 12f, image.top + 38f, image.right - 12f, image.top + 54f, paint)
    paint.color = AndroidColor.rgb(91, 51, 43)
    canvas.drawRect(image.left + 18f, image.top + 24f, image.right - 18f, image.top + 38f, paint)
}

private fun drawMuseumThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(223, 225, 231)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(122, 128, 143)
    canvas.drawRect(image.left + 16f, image.top + 38f, image.right - 16f, image.bottom - 18f, paint)
    paint.color = AndroidColor.rgb(95, 101, 115)
    val roof = android.graphics.Path().apply {
        moveTo(image.left + 12f, image.top + 38f)
        lineTo(image.centerX(), image.top + 20f)
        lineTo(image.right - 12f, image.top + 38f)
        close()
    }
    canvas.drawPath(roof, paint)
}

private fun drawVillageThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(226, 215, 197)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(93, 66, 50)
    canvas.drawRect(image.left + 18f, image.top + 40f, image.right - 18f, image.bottom - 16f, paint)
    paint.color = AndroidColor.rgb(62, 45, 37)
    val roof = android.graphics.Path().apply {
        moveTo(image.left + 10f, image.top + 42f)
        lineTo(image.centerX(), image.top + 20f)
        lineTo(image.right - 10f, image.top + 42f)
        close()
    }
    canvas.drawPath(roof, paint)
}

private fun drawStreetThumbnail(canvas: AndroidCanvas, paint: Paint, image: RectF) {
    paint.color = AndroidColor.rgb(238, 213, 217)
    canvas.drawRoundRect(image, 10f, 10f, paint)
    paint.color = AndroidColor.rgb(107, 88, 85)
    canvas.drawRect(image.left + 28f, image.top + 28f, image.right - 28f, image.bottom - 10f, paint)
    paint.color = AndroidColor.rgb(247, 202, 89)
    canvas.drawCircle(image.centerX(), image.top + 34f, 11f, paint)
    paint.color = AndroidColor.rgb(225, 112, 135)
    canvas.drawRect(image.left + 10f, image.bottom - 28f, image.right - 10f, image.bottom - 14f, paint)
}

private enum class CoursePinThumbnailKind {
    Cheomseongdae,
    Seokbinggo,
    PalacePond,
    Temple,
    Grotto,
    Tombs,
    Bridge,
    Museum,
    Village,
    Street,
}

private fun CourseMapPin.toThumbnailKind(): CoursePinThumbnailKind {
    val source = "$title $thumbnailUrl".lowercase()
    return when {
        source.contains("\uC11D\uBE59\uACE0") || source.contains("seokbinggo") -> CoursePinThumbnailKind.Seokbinggo
        source.contains("\uB3D9\uAD81") || source.contains("\uC6D4\uC9C0") ||
            source.contains("donggung") || source.contains("wolji") -> CoursePinThumbnailKind.PalacePond
        source.contains("\uBD88\uAD6D\uC0AC") || source.contains("bulguksa") -> CoursePinThumbnailKind.Temple
        source.contains("\uC11D\uAD74\uC554") || source.contains("seokguram") -> CoursePinThumbnailKind.Grotto
        source.contains("\uB300\uB989\uC6D0") || source.contains("daereungwon") || source.contains("daeneungwon") -> CoursePinThumbnailKind.Tombs
        source.contains("\uC6D4\uC815\uAD50") || source.contains("woljeonggyo") -> CoursePinThumbnailKind.Bridge
        source.contains("\uBC15\uBB3C\uAD00") || source.contains("museum") -> CoursePinThumbnailKind.Museum
        source.contains("\uAD50\uCD0C") || source.contains("gyochon") -> CoursePinThumbnailKind.Village
        source.contains("\uD669\uB9AC\uB2E8") || source.contains("hwangnidan") -> CoursePinThumbnailKind.Street
        else -> CoursePinThumbnailKind.Cheomseongdae
    }
}

@Composable
private fun CourseMockMap(
    visibleCandidates: List<CourseCandidate>,
    selectedIds: Set<String>,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 9.dp,
        border = BorderStroke(1.dp, Color.White),
    ) {
        val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
        val tertiaryContainerColor = MaterialTheme.colorScheme.tertiaryContainer
        val primaryColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(226.dp)
                .clip(RoundedCornerShape(28.dp)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val routePath: Path = Path().apply {
                    moveTo(size.width * 0.08f, size.height * 0.72f)
                    cubicTo(
                        size.width * 0.24f,
                        size.height * 0.42f,
                        size.width * 0.44f,
                        size.height * 0.92f,
                        size.width * 0.58f,
                        size.height * 0.56f,
                    )
                    cubicTo(
                        size.width * 0.70f,
                        size.height * 0.24f,
                        size.width * 0.84f,
                        size.height * 0.38f,
                        size.width * 0.92f,
                        size.height * 0.20f,
                    )
                }
                drawCircle(
                    color = surfaceContainerColor.copy(alpha = 0.82f),
                    radius = minOf(size.width, size.height) * 0.26f,
                    center = Offset(size.width * 0.20f, size.height * 0.30f),
                )
                drawCircle(
                    color = tertiaryContainerColor.copy(alpha = 0.36f),
                    radius = minOf(size.width, size.height) * 0.22f,
                    center = Offset(size.width * 0.82f, size.height * 0.76f),
                )
                drawLine(
                color = Color.White,
                    start = Offset(size.width * 0.08f, size.height * 0.28f),
                    end = Offset(size.width * 0.88f, size.height * 0.18f),
                    strokeWidth = 14f,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFFFFE3A6),
                    start = Offset(size.width * 0.16f, size.height * 0.88f),
                    end = Offset(size.width * 0.90f, size.height * 0.70f),
                    strokeWidth = 10f,
                    cap = StrokeCap.Round,
                )
                drawPath(
                    path = routePath,
                    color = primaryColor.copy(alpha = 0.22f),
                    style = Stroke(width = 18f, cap = StrokeCap.Round),
                )
                drawPath(
                    path = routePath,
                    color = primaryColor,
                    style = Stroke(width = 7f, cap = StrokeCap.Round),
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "목업 지도",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            MapMarker(
                label = visibleCandidates.getOrNull(0)?.shortTitle ?: "출발",
                isSelected = hasSelectedCandidate(
                    candidate = visibleCandidates.getOrNull(0),
                    selectedIds = selectedIds,
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 36.dp, y = 92.dp),
            )
            MapMarker(
                label = visibleCandidates.getOrNull(1)?.shortTitle ?: "기록",
                isSelected = hasSelectedCandidate(
                    candidate = visibleCandidates.getOrNull(1),
                    selectedIds = selectedIds,
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 18.dp, y = 24.dp),
            )
            MapMarker(
                label = visibleCandidates.getOrNull(2)?.shortTitle ?: "도착",
                isSelected = hasSelectedCandidate(
                    candidate = visibleCandidates.getOrNull(2),
                    selectedIds = selectedIds,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-28).dp, y = 58.dp),
            )
        }
    }
}

private fun hasSelectedCandidate(
    candidate: CourseCandidate?,
    selectedIds: Set<String>,
): Boolean = candidate?.id?.let { candidateId ->
    candidateId in selectedIds
} ?: false

@Composable
private fun MapMarker(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
): Unit {
    val containerColor: Color = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color.White
    val borderColor: Color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SegmentedControl(
    options: List<SegmentOption>,
    selectedOptionId: String,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val isSelected: Boolean = option.id == selectedOptionId
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .tossClickable(
                            role = Role.Button,
                            pressedScale = CultureMotion.SubtlePressedScale,
                            onClick = { onOptionSelected(option.id) },
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                    shadowElevation = if (isSelected) 3.dp else 0.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun CourseCandidateCard(
    candidate: CourseCandidate,
    isSelected: Boolean,
    selectedPlaceCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val hasPartialSelection: Boolean = !isSelected && selectedPlaceCount > 0
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .tossClickable(
                role = Role.Button,
                pressedScale = CultureMotion.SubtlePressedScale,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected || hasPartialSelection) MaterialTheme.colorScheme.primary else Color.White,
        ),
        shadowElevation = if (isSelected || hasPartialSelection) 8.dp else 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CourseCandidateThumbnail(
                candidate = candidate,
                isSelected = isSelected,
                modifier = Modifier.size(58.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = candidate.kind,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = candidate.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InfoPill(text = candidate.distance)
                    InfoPill(text = candidate.duration)
                    InfoPill(text = candidate.stops)
                    if (candidate.placeIds.size > 1 && selectedPlaceCount > 0) {
                        InfoPill(text = "${selectedPlaceCount}/${candidate.placeIds.size} 담김")
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseCandidateThumbnail(
    candidate: CourseCandidate,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
): Unit {
    val placeId: Long? = candidate.placeIds.singleOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        candidate.accentColor.copy(alpha = 0.84f),
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.78f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (placeId != null && candidate.thumbnailUrl?.isNotBlank() == true) {
            CoursePlaceThumbnail(
                placeId = placeId,
                title = candidate.title,
                thumbnailUrl = candidate.thumbnailUrl,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (isSelected) 0.18f else 0.08f)),
            )
        }
        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Add,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = Color.White,
        )
    }
}

@Composable
private fun SelectionBasket(
    selectedPlaces: List<CourseSelectedPlace>,
    onRemove: (Long) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color.White,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                start = 20.dp,
                top = if (isLandscape) 10.dp else 14.dp,
                end = 20.dp,
                bottom = if (isLandscape) 10.dp else 18.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "내 코스 담기",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${selectedPlaces.size}곳 담김",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Text(
                        text = "최대 ${MaxSelectedPlaceCount}곳",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (selectedPlaces.isEmpty()) {
                EmptyBasketMessage()
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 2.dp),
                ) {
                    items(
                        items = selectedPlaces,
                        key = { item -> item.id },
                    ) { item ->
                        BasketChip(
                            selectedPlace = item,
                            onRemove = { onRemove(item.id) },
                        )
                    }
                }
                Text(
                    text = "방문 순서는 다음 페이지에서 바꿀 수 있어요",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onNext,
                enabled = selectedPlaces.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 48.dp else 54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Text(
                    text = "다음",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun EmptyBasketMessage(
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = "마음에 드는 장소나 코스를 담아보세요",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseStatusMessage(
    text: String,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BasketChip(
    selectedPlace: CourseSelectedPlace,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
): Unit {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, Color.White),
    ) {
        Row(
            modifier = Modifier.padding(start = 7.dp, top = 7.dp, end = 6.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CoursePlaceThumbnail(
                placeId = selectedPlace.id,
                title = selectedPlace.title,
                thumbnailUrl = selectedPlace.thumbnailUrl,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = selectedPlace.shortTitle,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "${selectedPlace.title} 제거",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun CourseSectionTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
): Unit {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseBackdrop(
    modifier: Modifier = Modifier,
): Unit {
    Canvas(modifier = modifier.fillMaxSize()) {
        repeat(14) { index ->
            val x: Float = ((index * 67) % 100) / 100f * size.width
            val y: Float = ((index * 41) % 100) / 100f * size.height
            drawCircle(
                color = Color.White.copy(alpha = 0.44f),
                radius = 3.5f + (index % 3) * 1.6f,
                center = Offset(x, y),
            )
        }
    }
}

enum class CourseSelectMode(
    val id: String,
    val label: String,
) {
    Distance(id = "distance", label = "거리별 코스"),
    Recommended(id = "recommended", label = "추천 코스"),
}

private data class SegmentOption(
    val id: String,
    val label: String,
)

data class CourseCandidate(
    val id: String,
    val kind: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val distance: String,
    val duration: String,
    val stops: String,
    val accentColor: Color,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val thumbnailUrl: String? = null,
    val placeIds: List<Long> = emptyList(),
    val placeTitles: List<String> = emptyList(),
)

data class CourseSelectedPlace(
    val id: Long,
    val title: String,
    val shortTitle: String,
    val thumbnailUrl: String?,
)

data class CourseMapPin(
    val placeId: Long,
    val candidateId: String,
    val order: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val description: String,
    val distanceLabel: String,
    val thumbnailUrl: String?,
)

data class CourseSelectRouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<CourseSelectRoutePoint>,
) {
    val distanceLabel: String
        get() = distanceMeters.toRouteDistanceLabel()
    val durationLabel: String
        get() = durationSeconds.toRouteDurationLabel()
}

data class CourseSelectRoutePoint(
    val latitude: Double,
    val longitude: Double,
)

private fun CourseCandidate.toCourseIdOrNull(): Long? =
    id.removePrefix(CourseCandidateCoursePrefix)
        .takeIf { rawId -> id.startsWith(CourseCandidateCoursePrefix) && rawId.isNotBlank() }
        ?.toLongOrNull()

private fun resolveCoursePlaceThumbnailModel(
    placeId: Long,
    title: String,
    thumbnailUrl: String?,
): Any? =
    resolveCourseMapMarkerThumbnailResourceId(
        placeId = placeId,
        title = title,
        thumbnailUrl = thumbnailUrl,
    ) ?: resolveCoursePlaceThumbnailUrl(
        placeId = placeId,
        title = title,
        thumbnailUrl = thumbnailUrl,
    )

private fun CourseMapPin.resolveCourseMapMarkerThumbnailResourceId(): Int? =
    resolveCourseMapMarkerThumbnailResourceId(
        placeId = placeId,
        title = title,
        thumbnailUrl = thumbnailUrl,
    )

private fun resolveCourseMapMarkerThumbnailResourceId(
    placeId: Long,
    title: String,
    thumbnailUrl: String?,
): Int? {
    val normalizedSource = listOfNotNull(thumbnailUrl, title)
        .joinToString(separator = " ")
        .lowercase()
    return CoursePlaceMarkerThumbnailResourceIds[placeId]
        ?: CoursePlaceMarkerThumbnailKeywords.firstNotNullOfOrNull { (keyword, resourceId) ->
            resourceId.takeIf { normalizedSource.contains(keyword) }
        }
}

private fun resolveCoursePlaceThumbnailUrl(
    placeId: Long,
    title: String,
    thumbnailUrl: String?,
): String? {
    thumbnailUrl?.takeIf(String::isNotBlank)?.let { return it }
    val normalizedSource = listOfNotNull(thumbnailUrl, title)
        .joinToString(separator = " ")
        .lowercase()
    return CoursePlaceThumbnailUrls[placeId]
        ?: CoursePlaceThumbnailKeywords.firstNotNullOfOrNull { (keyword, url) ->
            url.takeIf { normalizedSource.contains(keyword) }
        }
}

private data class CourseSelectLoadResult(
    val candidates: CourseCandidateGroups,
    val mapPins: List<CourseMapPin>,
)

private data class CourseCandidateGroups(
    val distanceGroups: Map<String, List<CourseCandidate>>,
    val recommendationGroups: Map<String, List<CourseCandidate>>,
)

private data class CourseFallbackPinPosition(
    val xOffset: androidx.compose.ui.unit.Dp,
    val yOffset: androidx.compose.ui.unit.Dp,
)

private fun buildCourseCandidates(
    courses: List<CourseSummary>,
    places: List<PlaceSummary>,
): CourseCandidateGroups {
    val distanceGroups = buildDistanceCandidateGroups(places)
    val recommendationGroups = buildRecommendationCandidateGroups(courses)
    return CourseCandidateGroups(
        distanceGroups = if (distanceGroups.values.any { candidates -> candidates.isNotEmpty() }) {
            distanceGroups
        } else {
            DistanceCandidateGroups
        },
        recommendationGroups = if (recommendationGroups.values.any { candidates -> candidates.isNotEmpty() }) {
            recommendationGroups
        } else {
            RecommendationCandidateGroups
        },
    )
}

private fun buildDistanceCandidateGroups(
    places: List<PlaceSummary>,
): Map<String, List<CourseCandidate>> {
    val candidates = places.mapIndexed { index, place ->
        CourseCandidate(
            id = "place-${place.id}",
            kind = "장소",
            title = place.title,
            shortTitle = place.title.toShortTitle(defaultValue = "장소"),
            description = "지도 위치가 등록된 경주 문화 장소",
            distance = PlaceDistanceLabels[index % PlaceDistanceLabels.size],
            duration = PlaceDurationLabels[index % PlaceDurationLabels.size],
            stops = "1곳",
            accentColor = CandidateAccentColors[index % CandidateAccentColors.size],
            latitude = place.latitude,
            longitude = place.longitude,
            thumbnailUrl = place.thumbnailUrl,
            placeIds = listOf(place.id),
            placeTitles = listOf(place.title),
        )
    }
    return DistanceOptions.associate { option -> option.id to candidates }
}

private fun buildRecommendationCandidateGroups(
    courses: List<CourseSummary>,
): Map<String, List<CourseCandidate>> {
    val candidates = courses.mapIndexed { index, course ->
        val stopCount = course.places.size.coerceAtLeast(1)
        CourseCandidate(
            id = "course-${course.id}",
            kind = "추천 코스",
            title = course.title,
            shortTitle = course.title.toShortTitle(defaultValue = "코스"),
            description = course.description.ifBlank {
                "${stopCount}개 장소를 잇는 추천 코스"
            },
            distance = CourseDistanceLabels[index % CourseDistanceLabels.size],
            duration = "${stopCount * 18 + 10}분",
            stops = "${stopCount}곳",
            accentColor = CandidateAccentColors[(index + 1) % CandidateAccentColors.size],
            placeIds = course.places.map { place -> place.id },
            placeTitles = course.places.map { place -> place.title },
        )
    }
    return RecommendationOptions.associate { option ->
        option.id to candidates
    }
}

private fun String.toShortTitle(
    defaultValue: String,
): String {
    val compactTitle = filter { char -> !char.isWhitespace() }
    return compactTitle.take(5).ifBlank { defaultValue }
}

private fun String.toPinTitle(): String =
    if (length <= CoursePinTitleMaxLength) {
        this
    } else {
        "${take(CoursePinTitleMaxLength - 1)}…"
    }

private fun PlaceSummary.toCourseMapPin(
    order: Int,
): CourseMapPin? {
    val latitude = latitude ?: return null
    val longitude = longitude ?: return null
    return CourseMapPin(
        placeId = id,
        candidateId = "place-$id",
        order = order,
        title = title,
        latitude = latitude,
        longitude = longitude,
        address = "핀을 눌러 상세 주소를 확인해보세요.",
        description = "문화재 설명과 코스 정보를 바텀 시트에서 확인할 수 있어요.",
        distanceLabel = CourseMapDistanceLabels[(order - 1) % CourseMapDistanceLabels.size],
        thumbnailUrl = thumbnailUrl,
    )
}

private fun PlaceDetail.toCourseMapPin(
    fallbackPin: CourseMapPin,
): CourseMapPin =
    CourseMapPin(
        placeId = id,
        candidateId = fallbackPin.candidateId,
        order = fallbackPin.order,
        title = title.ifBlank { fallbackPin.title },
        latitude = latitude ?: fallbackPin.latitude,
        longitude = longitude ?: fallbackPin.longitude,
        address = address,
        description = description.ifBlank { fallbackPin.description },
        distanceLabel = fallbackPin.distanceLabel,
        thumbnailUrl = thumbnailUrl ?: fallbackPin.thumbnailUrl,
    )

private fun CourseMapPin.toLatLng(): LatLng =
    LatLng.from(latitude, longitude)

private fun CourseMapPin.toRouteStop(): KakaoRouteStop =
    KakaoRouteStop(
        latitude = latitude,
        longitude = longitude,
    )

private fun CourseSelectRoutePoint.toLatLng(): LatLng =
    LatLng.from(latitude, longitude)

private fun KakaoCourseRoute.toSelectRouteInfo(): CourseSelectRouteInfo =
    CourseSelectRouteInfo(
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        points = points.map(KakaoRoutePoint::toSelectRoutePoint),
    )

private fun KakaoRoutePoint.toSelectRoutePoint(): CourseSelectRoutePoint =
    CourseSelectRoutePoint(
        latitude = latitude,
        longitude = longitude,
    )

private fun List<CourseMapPin>.orderedByPlaceIds(
    placeIds: List<Long>,
): List<CourseMapPin> {
    val pinsById: Map<Long, CourseMapPin> = associateBy(CourseMapPin::placeId)
    return placeIds.mapNotNull(pinsById::get)
}

private fun List<CourseMapPin>.toRouteStops(
    placeIds: List<Long>,
): List<KakaoRouteStop> =
    orderedByPlaceIds(placeIds).map(CourseMapPin::toRouteStop)

private fun Int.toRouteDurationLabel(): String {
    val minutes: Int = ((this + SecondsPerMinute - 1) / SecondsPerMinute).coerceAtLeast(1)
    if (minutes < MinutesPerHour) return "${minutes}분"
    val hours: Int = minutes / MinutesPerHour
    val remainingMinutes: Int = minutes % MinutesPerHour
    return if (remainingMinutes == 0) {
        "${hours}시간"
    } else {
        "${hours}시간 ${remainingMinutes}분"
    }
}

private fun Int.toRouteDistanceLabel(): String =
    if (this < MetersPerKilometer) {
        "${this}m"
    } else {
        val tenths: Int = this / 100
        "${tenths / 10}.${tenths % 10}km"
    }

private val ModeOptions: List<SegmentOption> = listOf(
    SegmentOption(id = CourseSelectMode.Distance.id, label = CourseSelectMode.Distance.label),
    SegmentOption(id = CourseSelectMode.Recommended.id, label = CourseSelectMode.Recommended.label),
)

private val DistanceOptions: List<SegmentOption> = listOf(
    SegmentOption(id = "easy", label = "가볍게 1km"),
    SegmentOption(id = "balanced", label = "알차게 2km"),
    SegmentOption(id = "wide", label = "여유롭게 3km"),
)

private val RecommendationOptions: List<SegmentOption> = listOf(
    SegmentOption(id = "popular", label = "인기"),
    SegmentOption(id = "story", label = "이야기"),
    SegmentOption(id = "family", label = "가족"),
)

private val DistanceCandidateGroups: Map<String, List<CourseCandidate>> = mapOf(
    "easy" to listOf(
        CourseCandidate(
            id = "place-stream-walk",
            kind = "장소",
            title = "천마천 산책길",
            shortTitle = "천마천",
            description = "물길을 따라 짧게 걷고 사진을 남기기 좋은 시작점",
            distance = "0.8km",
            duration = "18분",
            stops = "2곳",
            accentColor = Color(0xFFFFAFC7),
        ),
        CourseCandidate(
            id = "place-archive-lane",
            kind = "장소",
            title = "기록 골목",
            shortTitle = "기록골목",
            description = "작은 벽화와 오래된 가게가 이어지는 조용한 골목",
            distance = "1.0km",
            duration = "25분",
            stops = "3곳",
            accentColor = Color(0xFFFFCF54),
        ),
        CourseCandidate(
            id = "place-garden-table",
            kind = "장소",
            title = "햇살 정원 쉼터",
            shortTitle = "정원쉼터",
            description = "코스 사이에 잠깐 앉아 쉬기 좋은 작은 정원",
            distance = "0.6km",
            duration = "15분",
            stops = "2곳",
            accentColor = Color(0xFF9ED7C5),
        ),
    ),
    "balanced" to listOf(
        CourseCandidate(
            id = "place-market-color",
            kind = "장소",
            title = "다정 시장길",
            shortTitle = "시장길",
            description = "간식, 공방, 골목 풍경을 한 번에 담는 산책 코스",
            distance = "1.7km",
            duration = "42분",
            stops = "4곳",
            accentColor = Color(0xFFFFB7C9),
        ),
        CourseCandidate(
            id = "place-bridge-view",
            kind = "장소",
            title = "노을다리 전망",
            shortTitle = "노을다리",
            description = "해질녘에 색이 예쁜 다리와 강변 전망 포인트",
            distance = "2.0km",
            duration = "50분",
            stops = "4곳",
            accentColor = Color(0xFFFFCF54),
        ),
        CourseCandidate(
            id = "place-small-museum",
            kind = "장소",
            title = "동네 문화관",
            shortTitle = "문화관",
            description = "지역 이야기를 가볍게 둘러볼 수 있는 실내 스팟",
            distance = "1.5km",
            duration = "35분",
            stops = "3곳",
            accentColor = Color(0xFFCDB8FF),
        ),
    ),
    "wide" to listOf(
        CourseCandidate(
            id = "place-hill-loop",
            kind = "코스",
            title = "언덕과 강변 한 바퀴",
            shortTitle = "강변루프",
            description = "조금 더 걸으며 전망, 골목, 쉼터를 모두 지나는 루프형 코스",
            distance = "3.1km",
            duration = "75분",
            stops = "6곳",
            accentColor = Color(0xFFFFAFC7),
        ),
        CourseCandidate(
            id = "place-book-cafe-road",
            kind = "코스",
            title = "책방 카페 산책",
            shortTitle = "책방길",
            description = "동네 책방과 작은 카페를 잇는 여유로운 오후 코스",
            distance = "2.8km",
            duration = "70분",
            stops = "5곳",
            accentColor = Color(0xFFFFCF54),
        ),
        CourseCandidate(
            id = "place-night-light",
            kind = "코스",
            title = "밤빛 포토 루트",
            shortTitle = "밤빛루트",
            description = "조명이 켜지는 골목과 광장을 따라 걷는 야간 코스",
            distance = "3.0km",
            duration = "68분",
            stops = "5곳",
            accentColor = Color(0xFF9ED7C5),
        ),
    ),
)

private val RecommendationCandidateGroups: Map<String, List<CourseCandidate>> = mapOf(
    "popular" to listOf(
        CourseCandidate(
            id = "course-best-photo",
            kind = "추천 코스",
            title = "사진 잘 나오는 반나절",
            shortTitle = "포토코스",
            description = "인기 촬영 포인트와 쉬어가기 좋은 장소를 묶은 코스",
            distance = "2.4km",
            duration = "60분",
            stops = "5곳",
            accentColor = Color(0xFFFFB7C9),
        ),
        CourseCandidate(
            id = "course-first-visit",
            kind = "추천 코스",
            title = "처음 방문자를 위한 기본 코스",
            shortTitle = "첫방문",
            description = "대표 장소를 빠짐없이 지나가는 안정적인 입문 코스",
            distance = "2.1km",
            duration = "55분",
            stops = "4곳",
            accentColor = Color(0xFFFFCF54),
        ),
        CourseCandidate(
            id = "course-cafe-memory",
            kind = "추천 코스",
            title = "달콤한 카페 기억 코스",
            shortTitle = "카페기억",
            description = "디저트 가게와 작은 문화 공간을 함께 즐기는 코스",
            distance = "1.9km",
            duration = "48분",
            stops = "4곳",
            accentColor = Color(0xFFCDB8FF),
        ),
    ),
    "story" to listOf(
        CourseCandidate(
            id = "course-old-town",
            kind = "추천 코스",
            title = "오래된 마을 이야기",
            shortTitle = "마을이야기",
            description = "골목의 간판, 기록관, 오래된 나무를 따라가는 이야기 코스",
            distance = "2.6km",
            duration = "65분",
            stops = "5곳",
            accentColor = Color(0xFFFFAFC7),
        ),
        CourseCandidate(
            id = "course-artist-note",
            kind = "추천 코스",
            title = "작가의 노트",
            shortTitle = "작가노트",
            description = "작은 전시와 공방을 지나며 창작자의 흔적을 만나는 코스",
            distance = "1.8km",
            duration = "46분",
            stops = "4곳",
            accentColor = Color(0xFF9ED7C5),
        ),
        CourseCandidate(
            id = "course-memory-post",
            kind = "추천 코스",
            title = "느린 우체통 산책",
            shortTitle = "우체통",
            description = "편지를 쓰고 사진을 남기기 좋은 감성 장소 중심 코스",
            distance = "2.2km",
            duration = "52분",
            stops = "4곳",
            accentColor = Color(0xFFFFCF54),
        ),
    ),
    "family" to listOf(
        CourseCandidate(
            id = "course-family-easy",
            kind = "추천 코스",
            title = "아이와 함께 쉬운 길",
            shortTitle = "가족길",
            description = "유모차 이동과 쉬는 시간을 고려한 완만한 가족 코스",
            distance = "1.4km",
            duration = "40분",
            stops = "4곳",
            accentColor = Color(0xFFFFCF54),
        ),
        CourseCandidate(
            id = "course-play-picnic",
            kind = "추천 코스",
            title = "놀이터와 피크닉",
            shortTitle = "피크닉",
            description = "놀이 공간과 잔디 쉼터를 이어 아이가 지루하지 않은 코스",
            distance = "1.6km",
            duration = "45분",
            stops = "4곳",
            accentColor = Color(0xFF9ED7C5),
        ),
        CourseCandidate(
            id = "course-rainy-day",
            kind = "추천 코스",
            title = "비 오는 날 실내 코스",
            shortTitle = "실내코스",
            description = "문화관, 북카페, 체험 공간을 잇는 날씨 걱정 없는 코스",
            distance = "1.2km",
            duration = "38분",
            stops = "3곳",
            accentColor = Color(0xFFCDB8FF),
        ),
    ),
)

private val AllCandidates: List<CourseCandidate> = (
    DistanceCandidateGroups.values.flatten() + RecommendationCandidateGroups.values.flatten()
).distinctBy { candidate -> candidate.id }

private val CoursePlaceMarkerThumbnailResourceIds: Map<Long, Int> = mapOf(
    101L to R.drawable.course_pin_cheomseongdae,
    102L to R.drawable.course_pin_bulguksa,
    103L to R.drawable.course_pin_seokguram,
    104L to R.drawable.course_pin_daereungwon,
    105L to R.drawable.course_pin_woljeonggyo,
    106L to R.drawable.course_pin_donggung_wolji,
    107L to R.drawable.course_pin_gyeongju_museum,
    108L to R.drawable.course_pin_gyochon_village,
    109L to R.drawable.course_pin_hwangnidan_gil,
)

private val CoursePlaceMarkerThumbnailKeywords: List<Pair<String, Int>> = listOf(
    "cheomseongdae" to R.drawable.course_pin_cheomseongdae,
    "bulguksa" to R.drawable.course_pin_bulguksa,
    "seokguram" to R.drawable.course_pin_seokguram,
    "daereungwon" to R.drawable.course_pin_daereungwon,
    "daeneungwon" to R.drawable.course_pin_daereungwon,
    "cheonmachong" to R.drawable.course_pin_daereungwon,
    "cheonma" to R.drawable.course_pin_daereungwon,
    "tomb" to R.drawable.course_pin_daereungwon,
    "woljeonggyo" to R.drawable.course_pin_woljeonggyo,
    "donggung" to R.drawable.course_pin_donggung_wolji,
    "wolji" to R.drawable.course_pin_donggung_wolji,
    "museum" to R.drawable.course_pin_gyeongju_museum,
    "gyochon" to R.drawable.course_pin_gyochon_village,
    "hwangnidan" to R.drawable.course_pin_hwangnidan_gil,
    "\uCCA8\uC131\uB300" to R.drawable.course_pin_cheomseongdae,
    "\uBD88\uAD6D\uC0AC" to R.drawable.course_pin_bulguksa,
    "\uC11D\uAD74\uC554" to R.drawable.course_pin_seokguram,
    "\uB300\uB989\uC6D0" to R.drawable.course_pin_daereungwon,
    "\uCC9C\uB9C8\uCD1D" to R.drawable.course_pin_daereungwon,
    "\uACE0\uBD84" to R.drawable.course_pin_daereungwon,
    "\uC655\uB989" to R.drawable.course_pin_daereungwon,
    "\uC6D4\uC815\uAD50" to R.drawable.course_pin_woljeonggyo,
    "\uB3D9\uAD81" to R.drawable.course_pin_donggung_wolji,
    "\uC6D4\uC9C0" to R.drawable.course_pin_donggung_wolji,
    "\uAD6D\uB9BD\uACBD\uC8FC\uBC15\uBB3C\uAD00" to R.drawable.course_pin_gyeongju_museum,
    "\uAD50\uCD0C" to R.drawable.course_pin_gyochon_village,
    "\uD669\uB9AC\uB2E8\uAE38" to R.drawable.course_pin_hwangnidan_gil,
)

private val CoursePlaceThumbnailUrls: Map<Long, String> = mapOf(
    101L to "https://commons.wikimedia.org/wiki/Special:FilePath/Cheomseongdae%2C%20Gyeongju.jpg",
    102L to "https://commons.wikimedia.org/wiki/Special:FilePath/Korea-Gyeongju-Bulguksa-20.jpg",
    103L to "https://commons.wikimedia.org/wiki/Special:FilePath/Seokguram%20Grotto%2001.jpg",
    104L to "https://commons.wikimedia.org/wiki/Special:FilePath/Daereungwon.jpg",
    105L to "https://commons.wikimedia.org/wiki/Special:FilePath/Woljeonggyo%20Bridge.jpg",
    106L to "https://commons.wikimedia.org/wiki/Special:FilePath/Donggung%20Palace%20and%20Wolji%20Pond%20in%20Gyeongju.jpg",
    107L to "https://commons.wikimedia.org/wiki/Special:FilePath/Gyeongju%20National%20Museum.jpg",
    108L to "https://commons.wikimedia.org/wiki/Special:FilePath/Gyochon%20Village%203.jpg",
    109L to "https://commons.wikimedia.org/wiki/Special:FilePath/Hwangnidan-gil%2002.jpg",
)

private val CoursePlaceThumbnailKeywords: List<Pair<String, String>> = listOf(
    "cheomseongdae" to CoursePlaceThumbnailUrls.getValue(101L),
    "bulguksa" to CoursePlaceThumbnailUrls.getValue(102L),
    "seokguram" to CoursePlaceThumbnailUrls.getValue(103L),
    "daereungwon" to CoursePlaceThumbnailUrls.getValue(104L),
    "daeneungwon" to CoursePlaceThumbnailUrls.getValue(104L),
    "woljeonggyo" to CoursePlaceThumbnailUrls.getValue(105L),
    "donggung" to CoursePlaceThumbnailUrls.getValue(106L),
    "wolji" to CoursePlaceThumbnailUrls.getValue(106L),
    "museum" to CoursePlaceThumbnailUrls.getValue(107L),
    "gyochon" to CoursePlaceThumbnailUrls.getValue(108L),
    "hwangnidan" to CoursePlaceThumbnailUrls.getValue(109L),
    "\uCCA8\uC131\uB300" to CoursePlaceThumbnailUrls.getValue(101L),
    "\uBD88\uAD6D\uC0AC" to CoursePlaceThumbnailUrls.getValue(102L),
    "\uC11D\uAD74\uC554" to CoursePlaceThumbnailUrls.getValue(103L),
    "\uB300\uB989\uC6D0" to CoursePlaceThumbnailUrls.getValue(104L),
    "\uC6D4\uC815\uAD50" to CoursePlaceThumbnailUrls.getValue(105L),
    "\uB3D9\uAD81" to CoursePlaceThumbnailUrls.getValue(106L),
    "\uC6D4\uC9C0" to CoursePlaceThumbnailUrls.getValue(106L),
    "\uAD6D\uB9BD\uACBD\uC8FC\uBC15\uBB3C\uAD00" to CoursePlaceThumbnailUrls.getValue(107L),
    "\uAD50\uCD0C" to CoursePlaceThumbnailUrls.getValue(108L),
    "\uD669\uB9AC\uB2E8\uAE38" to CoursePlaceThumbnailUrls.getValue(109L),
)

private val CourseBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFB7C9),
        Color(0xFFFFE3ED),
        Color(0xFFFFF4CE),
    ),
)
private const val FirstCoursePlacePage: Int = 0
private const val CoursePlacePageSize: Int = 100
private const val MaxSelectedPlaceCount: Int = 5
private const val DefaultCourseId: Long = 1L
private const val CourseCandidateCoursePrefix: String = "course-"
private const val MinimumRouteStopCount: Int = 2
private const val MapGestureScrollLockTimeoutMillis: Long = 1_500L
private val CandidateAccentColors: List<Color> = listOf(
    Color(0xFFFFAFC7),
    Color(0xFFFFCF54),
    Color(0xFF9ED7C5),
    Color(0xFFCDB8FF),
)
private val PlaceDistanceLabels: List<String> = listOf("0.8km", "1.0km", "1.2km", "1.5km")
private val PlaceDurationLabels: List<String> = listOf("18분", "22분", "28분", "35분")
private val CourseDistanceLabels: List<String> = listOf("2.1km", "2.4km", "2.8km", "3.0km")
private val CourseMapDistanceLabels: List<String> = listOf("265m", "410m", "530m", "720m", "900m")
private val CourseFallbackPinPositions: List<CourseFallbackPinPosition> = listOf(
    CourseFallbackPinPosition(xOffset = 202.dp, yOffset = 52.dp),
    CourseFallbackPinPosition(xOffset = 12.dp, yOffset = 198.dp),
    CourseFallbackPinPosition(xOffset = 98.dp, yOffset = 248.dp),
    CourseFallbackPinPosition(xOffset = 258.dp, yOffset = 226.dp),
    CourseFallbackPinPosition(xOffset = 72.dp, yOffset = 72.dp),
    CourseFallbackPinPosition(xOffset = 222.dp, yOffset = 282.dp),
)
private val CourseMapHeight = 340.dp
private val CourseMapFitPadding = 58.dp
private const val DefaultCourseMapLatitude: Double = 35.8347
private const val DefaultCourseMapLongitude: Double = 129.2187
private const val CourseMapZoomLevel: Int = 11
private const val CourseSelectedMapZoomLevel: Int = 14
private const val CoursePinBitmapWidth: Int = 152
private const val CoursePinBitmapHeight: Int = 156
private const val CourseMapMarkerThumbnailDecodeSize: Int = 256
private const val CoursePinTitleMaxLength: Int = 6
private const val CourseMapRouteLineWidth: Float = 13f
private const val CourseMapRouteStrokeWidth: Float = 5f
private const val CourseMapRouteLineZOrder: Int = 0
private const val CourseMapRouteStyleId: String = "course-select-route-style"
private const val CourseMapRouteLineId: String = "course-select-route-line"
private const val SecondsPerMinute: Int = 60
private const val MinutesPerHour: Int = 60
private const val MetersPerKilometer: Int = 1000
