package com.ssafy.culture.ui.screen.course

import android.graphics.Bitmap
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.view.MotionEvent
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kakao.vectormap.GestureType
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
import com.ssafy.culture.data.course.DefaultCourseRepository
import com.ssafy.culture.data.repository.KakaoCourseRoute
import com.ssafy.culture.data.repository.KakaoDirectionsRepository
import com.ssafy.culture.data.repository.KakaoRoutePoint
import com.ssafy.culture.data.repository.KakaoRouteStop
import com.ssafy.culture.data.story.StoryRepository
import com.ssafy.culture.domain.model.CoursePlace
import com.ssafy.culture.domain.model.CourseSelectionDraft
import com.ssafy.culture.domain.model.PlaceDetail
import com.ssafy.culture.domain.model.StoryCreateRequest
import com.ssafy.culture.domain.model.StoryProtagonist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class CourseOrderUiState(
    val courseId: Long = DefaultCourseId,
    val places: List<CourseOrderPlace> = emptyList(),
    val route: CourseOrderRouteInfo? = null,
    val isLoading: Boolean = true,
    val isRouteLoading: Boolean = false,
    val isCreatingStory: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CourseOrderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val courseRepository: DefaultCourseRepository,
    private val storyRepository: StoryRepository,
    private val directionsRepository: KakaoDirectionsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CourseOrderUiState())
    val uiState: StateFlow<CourseOrderUiState> = _uiState.asStateFlow()
    private val courseId: Long = savedStateHandle[CourseIdArgument] ?: DefaultCourseId
    private var routeJob: Job? = null

    init {
        loadSelectedPlaces()
    }

    private fun loadSelectedPlaces() {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isLoading = true, errorMessage = null)
            }
            runCatching {
                val draft: CourseSelectionDraft? = courseRepository.getCourseSelectionDraft()
                val courses = courseRepository.getCourses()
                val fallbackCourseId = courses.firstOrNull()?.id ?: DefaultCourseId
                val requestedCourseId = draft?.courseId ?: courseId
                val targetCourseId = courses.firstOrNull { course -> course.id == requestedCourseId }?.id
                    ?: fallbackCourseId
                val draftPlaces = draft?.placeIds.orEmpty()
                val places = if (draftPlaces.isNotEmpty()) {
                    loadDraftPlaces(draftPlaces).ifEmpty {
                        courseRepository.getCourseDetail(targetCourseId).places.map(CoursePlace::toOrderPlace)
                    }
                } else {
                    courseRepository.getCourseDetail(targetCourseId).places.map(CoursePlace::toOrderPlace)
                }
                CourseOrderLoadResult(courseId = targetCourseId, places = places)
            }.onSuccess { result ->
                saveDraft(
                    courseId = result.courseId,
                    places = result.places,
                )
                _uiState.update { state ->
                    state.copy(
                        courseId = result.courseId,
                        places = result.places,
                        isLoading = false,
                    )
                }
                loadCourseRoute(result.places)
            }.onFailure { throwable ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = throwable.localizedMessage ?: "선택한 장소를 불러오지 못했어요.",
                    )
                }
            }
        }
    }

    fun removePlace(placeId: Long) {
        updatePlaces { places ->
            places.filterNot { place -> place.id == placeId }
        }
    }

    fun movePlace(placeId: Long, offset: Int) {
        updatePlaces { places ->
            val fromIndex: Int = places.indexOfFirst { place -> place.id == placeId }
            if (fromIndex == -1) return@updatePlaces places
            val toIndex: Int = (fromIndex + offset).coerceIn(0, places.lastIndex)
            if (fromIndex == toIndex) return@updatePlaces places
            places.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    fun createStory(onCreated: (Long) -> Unit) {
        val state: CourseOrderUiState = _uiState.value
        if (state.places.isEmpty() || state.isCreatingStory) return
        saveDraft(
            courseId = state.courseId,
            places = state.places,
        )
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isCreatingStory = true, errorMessage = null)
            }
            runCatching {
                val protagonists: List<StoryProtagonist> = storyRepository.getProtagonistDraft()
                check(protagonists.canCreateStory()) {
                    "주인공 정보를 먼저 입력해 주세요."
                }
                storyRepository.createStory(
                    StoryCreateRequest(
                        courseId = state.courseId,
                        protagonists = protagonists,
                        placeIds = state.places.map { place -> place.id },
                    ),
                )
            }.onSuccess { story ->
                _uiState.update { currentState ->
                    currentState.copy(isCreatingStory = false)
                }
                onCreated(story.storyId)
            }.onFailure { throwable ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isCreatingStory = false,
                        errorMessage = throwable.localizedMessage ?: "이야기를 만들지 못했어요.",
                    )
                }
            }
        }
    }

    private suspend fun loadDraftPlaces(placeIds: List<Long>): List<CourseOrderPlace> =
        placeIds.distinct().mapNotNull { placeId ->
            runCatching {
                courseRepository.getPlaceDetail(placeId).toOrderPlace()
            }.getOrNull()
        }

    private fun updatePlaces(transform: (List<CourseOrderPlace>) -> List<CourseOrderPlace>) {
        val state: CourseOrderUiState = _uiState.value
        val updatedPlaces: List<CourseOrderPlace> = transform(state.places)
        _uiState.update { currentState ->
            currentState.copy(places = updatedPlaces)
        }
        saveDraft(
            courseId = state.courseId,
            places = updatedPlaces,
        )
        loadCourseRoute(updatedPlaces)
    }

    private fun saveDraft(
        courseId: Long,
        places: List<CourseOrderPlace>,
    ) {
        courseRepository.saveCourseSelectionDraft(
            CourseSelectionDraft(
                courseId = courseId,
                placeIds = places.map { place -> place.id },
            ),
        )
    }

    private fun loadCourseRoute(
        places: List<CourseOrderPlace>,
    ) {
        val routeStops: List<KakaoRouteStop> = places.mapNotNull(CourseOrderPlace::toRouteStop)
        routeJob?.cancel()
        if (routeStops.size < MinimumRouteStopCount) {
            _uiState.update { state ->
                state.copy(route = null, isRouteLoading = false)
            }
            return
        }
        routeJob = viewModelScope.launch {
            _uiState.update { state ->
                state.copy(isRouteLoading = true)
            }
            runCatching {
                directionsRepository.getCourseRoute(routeStops)
            }.onSuccess { route ->
                _uiState.update { state ->
                    state.copy(
                        route = route?.toOrderRouteInfo(),
                        isRouteLoading = false,
                    )
                }
            }.onFailure {
                _uiState.update { state ->
                    state.copy(
                        route = null,
                        isRouteLoading = false,
                    )
                }
            }
        }
    }
}

@Composable
fun CourseOrderRoute(
    onBack: () -> Unit,
    onComplete: (Long) -> Unit,
    viewModel: CourseOrderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    CourseOrderScreen(
        uiState = uiState,
        onBack = onBack,
        onMovePlace = viewModel::movePlace,
        onRemovePlace = viewModel::removePlace,
        onComplete = { viewModel.createStory(onComplete) },
    )
}

@Composable
fun CourseOrderScreen(
    uiState: CourseOrderUiState,
    onBack: () -> Unit,
    onMovePlace: (Long, Int) -> Unit,
    onRemovePlace: (Long) -> Unit,
    onComplete: () -> Unit,
) {
    val places = uiState.places
    val isLandscape: Boolean = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val footerContentPadding = if (isLandscape) 96.dp else 118.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFB0C7),
                        Color(0xFFFFD6BF),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        CourseOrderGlow()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 18.dp,
                top = if (isLandscape) 18.dp else 26.dp,
                end = 18.dp,
                bottom = footerContentPadding + navigationBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp),
        ) {
            item {
                CourseOrderHeader(onBack = onBack)
            }
            item {
                Text(
                    text = "방문 순서를 원하는 대로 조절해보세요",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (places.any { place -> place.hasLocation() }) {
                item {
                    CourseOrderMapCard(
                        places = places,
                        route = uiState.route,
                        isRouteLoading = uiState.isRouteLoading,
                    )
                }
            }
            if (uiState.isLoading || uiState.errorMessage != null || places.isEmpty()) {
                item {
                    CourseOrderStatusMessage(
                        text = when {
                            uiState.errorMessage != null -> uiState.errorMessage
                            uiState.isLoading -> "선택한 장소를 불러오는 중이에요."
                            else -> "선택한 장소가 없어요."
                        },
                    )
                }
            }
            itemsIndexed(
                items = places,
                key = { _, place -> place.id },
            ) { index, place ->
                CourseOrderPlaceRow(
                    order = index + 1,
                    place = place,
                    onMove = onMovePlace,
                    onRemove = { onRemovePlace(place.id) },
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = Color.White,
            shadowElevation = 18.dp,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = if (isLandscape) 10.dp else 14.dp),
            ) {
                Button(
                    onClick = onComplete,
                    enabled = places.isNotEmpty() && !uiState.isCreatingStory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isLandscape) 50.dp else 56.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onBackground,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isCreatingStory) "이야기 만드는 중" else "이야기 만들기",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    )
                }
            }
        }

        if (uiState.isCreatingStory) {
            StoryCreatingOverlay(
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun StoryCreatingOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 18.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 4.dp,
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "이야기를 생성중이에요",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Text(
                        text = "잠시만 기다려주세요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseOrderStatusMessage(
    text: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CourseOrderMapCard(
    places: List<CourseOrderPlace>,
    route: CourseOrderRouteInfo?,
    isRouteLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val mapPlaces: List<CourseOrderMapPlace> = places.mapIndexedNotNull { index, place ->
        place.toMapPlace(order = index + 1)
    }
    if (mapPlaces.isEmpty()) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CourseOrderMapHeight)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
                CourseOrderFallbackMap(
                    mapPlaces = mapPlaces,
                    route = route,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CourseOrderKakaoMap(
                    mapPlaces = mapPlaces,
                    route = route,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            CourseOrderMapBadge(
                count = mapPlaces.size,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            )
            CourseOrderRouteBadge(
                route = route,
                isLoading = isRouteLoading,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun CourseOrderKakaoMap(
    mapPlaces: List<CourseOrderMapPlace>,
    route: CourseOrderRouteInfo?,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val fitPaddingPx: Int = with(LocalDensity.current) {
        CourseOrderMapFitPadding.roundToPx()
    }
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val mapView = remember {
        MapView(context).also { view ->
            view.setOnTouchListener { touchedView, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE,
                    -> touchedView.parent?.requestDisallowInterceptTouchEvent(true)

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_CANCEL,
                    -> touchedView.parent?.requestDisallowInterceptTouchEvent(false)
                }
                false
            }
            view.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit
                    override fun onMapError(error: Exception?) = Unit
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(readyMap: KakaoMap) {
                        disableCourseOrderMapGestures(readyMap)
                        kakaoMap = readyMap
                    }

                    override fun getPosition(): LatLng =
                        mapPlaces.firstOrNull()?.toLatLng()
                            ?: LatLng.from(DefaultCourseMapLatitude, DefaultCourseMapLongitude)

                    override fun getZoomLevel(): Int = CourseOrderDefaultMapZoomLevel
                },
            )
        }
    }
    LaunchedEffect(kakaoMap, mapPlaces, route, fitPaddingPx) {
        kakaoMap?.let { readyMap ->
            renderCourseOrderRouteLine(
                kakaoMap = readyMap,
                route = route,
            )
            renderCourseOrderMapPins(
                kakaoMap = readyMap,
                mapPlaces = mapPlaces,
            )
            moveCourseOrderMapCamera(
                kakaoMap = readyMap,
                mapPlaces = mapPlaces,
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
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.pause() }
            runCatching { mapView.finish() }
        }
    }
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapView },
        )
        CourseOrderMapZoomControls(
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

@Composable
private fun CourseOrderFallbackMap(
    mapPlaces: List<CourseOrderMapPlace>,
    route: CourseOrderRouteInfo?,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.background(Color(0xFFE6E3DD)),
    ) {
        repeat(11) { index ->
            val y = size.height * (index / 10f)
            drawLine(
                color = Color.White.copy(alpha = 0.5f),
                start = Offset(0f, y),
                end = Offset(size.width, y - size.height * 0.18f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
        }
        repeat(8) { index ->
            val x = size.width * (index / 7f)
            drawLine(
                color = Color.White.copy(alpha = 0.38f),
                start = Offset(x, 0f),
                end = Offset(x - size.width * 0.18f, size.height),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        val bounds: CourseOrderCoordinateBounds = buildCourseOrderCoordinateBounds(
            mapPlaces = mapPlaces,
            routePoints = route?.points.orEmpty(),
        )
        val paddingPx: Float = CourseOrderFallbackMapPadding.toPx()
        route?.points?.takeIf { points -> points.size >= MinimumRouteStopCount }?.let { routePoints ->
            drawCourseOrderFallbackRoute(
                routePoints = routePoints,
                bounds = bounds,
                width = size.width,
                height = size.height,
                paddingPx = paddingPx,
            )
        }
        mapPlaces.forEach { place ->
            drawCourseOrderFallbackPin(
                anchor = place.toFallbackMapOffset(
                    bounds = bounds,
                    width = size.width,
                    height = size.height,
                    paddingPx = paddingPx,
                ),
                order = place.order,
            )
        }
    }
}

@Composable
private fun CourseOrderMapBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 5.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "방문 ${count}곳",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun CourseOrderRouteBadge(
    route: CourseOrderRouteInfo?,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
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
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CourseOrderMapZoomControls(
    enabled: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            CourseOrderMapZoomButton(
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
            CourseOrderMapZoomButton(
                enabled = enabled,
                icon = Icons.Rounded.Remove,
                contentDescription = "지도 축소",
                onClick = onZoomOut,
            )
        }
    }
}

@Composable
private fun CourseOrderMapZoomButton(
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
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
private fun CourseOrderGlow() {
    Box(
        modifier = Modifier
            .size(420.dp)
            .offset(y = 52.dp)
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
}

@Composable
private fun CourseOrderHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "뒤로가기",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = "코스 순서 조절",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            )
            Text(
                text = "나만의 문화 코스",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun CourseOrderPlaceRow(
    order: Int,
    place: CourseOrderPlace,
    onRemove: () -> Unit,
    onMove: (placeId: Long, direction: Int) -> Unit,
) {
    var isHandleDragging by remember { mutableStateOf(false) }
    var cardDragOffsetPx by remember { mutableStateOf(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(x = 0, y = cardDragOffsetPx.roundToInt()) },
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = if (isHandleDragging) 18.dp else 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CourseOrderNumber(order = order)
            Spacer(modifier = Modifier.width(14.dp))
            CoursePlaceIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = place.subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            CourseOrderIconAction(
                enabled = true,
                onClick = onRemove,
                icon = Icons.Rounded.Close,
                contentDescription = "${place.title} 제거",
            )
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .pointerInput(place.id) {
                        val dragThresholdPx = CourseOrderDragThreshold.toPx()
                        val cardTravelPx = CourseOrderCardTravel.toPx()
                        var reorderOffset = 0f
                        detectDragGestures(
                            onDragStart = {
                                isHandleDragging = true
                                reorderOffset = 0f
                                cardDragOffsetPx = 0f
                            },
                            onDragEnd = {
                                isHandleDragging = false
                                reorderOffset = 0f
                                cardDragOffsetPx = 0f
                            },
                            onDragCancel = {
                                isHandleDragging = false
                                reorderOffset = 0f
                                cardDragOffsetPx = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                reorderOffset += dragAmount.y
                                while (reorderOffset <= -dragThresholdPx) {
                                    onMove(place.id, -1)
                                    reorderOffset += dragThresholdPx
                                }
                                while (reorderOffset >= dragThresholdPx) {
                                    onMove(place.id, 1)
                                    reorderOffset -= dragThresholdPx
                                }
                                cardDragOffsetPx = reorderOffset.coerceIn(-cardTravelPx, cardTravelPx)
                            },
                        )
                    },
                shape = CircleShape,
                color = Color(0xFFFFE5EF),
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "순서 변경",
                        tint = Color(0xFFB48BA0),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseOrderIconAction(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(34.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color(0xFF6F5662) else Color(0xFFCFC4CA),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CourseOrderNumber(
    order: Int,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = order.toString(),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
        )
    }
}

@Composable
private fun CoursePlaceIcon() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFFE5EF)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
    }
}

data class CourseOrderPlace(
    val id: Long,
    val title: String,
    val subtitle: String,
    val latitude: Double?,
    val longitude: Double?,
)

private data class CourseOrderMapPlace(
    val id: Long,
    val order: Int,
    val latitude: Double,
    val longitude: Double,
)

data class CourseOrderRouteInfo(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<CourseOrderRoutePoint>,
) {
    val distanceLabel: String
        get() = distanceMeters.toRouteDistanceLabel()
    val durationLabel: String
        get() = durationSeconds.toRouteDurationLabel()
}

data class CourseOrderRoutePoint(
    val latitude: Double,
    val longitude: Double,
)

private data class CourseOrderCoordinate(
    val latitude: Double,
    val longitude: Double,
)

private data class CourseOrderCoordinateBounds(
    val minLatitude: Double,
    val maxLatitude: Double,
    val minLongitude: Double,
    val maxLongitude: Double,
)

private data class CourseOrderLoadResult(
    val courseId: Long,
    val places: List<CourseOrderPlace>,
)

private fun CoursePlace.toOrderPlace(): CourseOrderPlace =
    CourseOrderPlace(
        id = id,
        title = title,
        subtitle = if (latitude != null && longitude != null) {
            "방문 위치가 등록된 문화 장소"
        } else {
            "문화 장소"
        },
        latitude = latitude,
        longitude = longitude,
    )

private fun PlaceDetail.toOrderPlace(): CourseOrderPlace =
    CourseOrderPlace(
        id = id,
        title = title,
        subtitle = address.ifBlank {
            if (latitude != null && longitude != null) {
                "방문 위치가 등록된 문화 장소"
            } else {
                "문화 장소"
            }
        },
        latitude = latitude,
        longitude = longitude,
    )

private fun CourseOrderPlace.hasLocation(): Boolean =
    latitude != null && longitude != null

private fun CourseOrderPlace.toMapPlace(
    order: Int,
): CourseOrderMapPlace? {
    val latitude: Double = latitude ?: return null
    val longitude: Double = longitude ?: return null
    return CourseOrderMapPlace(
        id = id,
        order = order,
        latitude = latitude,
        longitude = longitude,
    )
}

private fun CourseOrderPlace.toRouteStop(): KakaoRouteStop? {
    val latitude: Double = latitude ?: return null
    val longitude: Double = longitude ?: return null
    return KakaoRouteStop(
        latitude = latitude,
        longitude = longitude,
    )
}

private fun List<StoryProtagonist>.canCreateStory(): Boolean =
    isNotEmpty() && all { protagonist ->
        protagonist.name.isNotBlank() &&
            protagonist.age > 0 &&
            protagonist.tendency.isNotBlank()
    }

private fun KakaoCourseRoute.toOrderRouteInfo(): CourseOrderRouteInfo =
    CourseOrderRouteInfo(
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        points = points.map(KakaoRoutePoint::toOrderRoutePoint),
    )

private fun KakaoRoutePoint.toOrderRoutePoint(): CourseOrderRoutePoint =
    CourseOrderRoutePoint(
        latitude = latitude,
        longitude = longitude,
    )

private fun CourseOrderMapPlace.toLatLng(): LatLng =
    LatLng.from(latitude, longitude)

private fun CourseOrderRoutePoint.toLatLng(): LatLng =
    LatLng.from(latitude, longitude)

private fun buildCourseOrderCoordinateBounds(
    mapPlaces: List<CourseOrderMapPlace>,
    routePoints: List<CourseOrderRoutePoint>,
): CourseOrderCoordinateBounds {
    val coordinates: List<CourseOrderCoordinate> = mapPlaces.map(CourseOrderMapPlace::toCoordinate) +
        routePoints.map(CourseOrderRoutePoint::toCoordinate)
    return coordinates.toCoordinateBounds()
}

private fun CourseOrderMapPlace.toCoordinate(): CourseOrderCoordinate =
    CourseOrderCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun CourseOrderRoutePoint.toCoordinate(): CourseOrderCoordinate =
    CourseOrderCoordinate(
        latitude = latitude,
        longitude = longitude,
    )

private fun List<CourseOrderCoordinate>.toCoordinateBounds(): CourseOrderCoordinateBounds =
    CourseOrderCoordinateBounds(
        minLatitude = minOf { coordinate -> coordinate.latitude },
        maxLatitude = maxOf { coordinate -> coordinate.latitude },
        minLongitude = minOf { coordinate -> coordinate.longitude },
        maxLongitude = maxOf { coordinate -> coordinate.longitude },
    )

private fun CourseOrderMapPlace.toFallbackMapOffset(
    bounds: CourseOrderCoordinateBounds,
    width: Float,
    height: Float,
    paddingPx: Float,
): Offset {
    val availableWidth: Float = (width - paddingPx * 2f).coerceAtLeast(1f)
    val availableHeight: Float = (height - paddingPx * 2f).coerceAtLeast(1f)
    val longitudeSpan: Double = bounds.maxLongitude - bounds.minLongitude
    val latitudeSpan: Double = bounds.maxLatitude - bounds.minLatitude
    val xRatio: Float = if (longitudeSpan > CourseOrderMinimumCoordinateSpan) {
        ((longitude - bounds.minLongitude) / longitudeSpan).toFloat().coerceIn(0f, 1f)
    } else {
        0.5f
    }
    val yRatio: Float = if (latitudeSpan > CourseOrderMinimumCoordinateSpan) {
        ((bounds.maxLatitude - latitude) / latitudeSpan).toFloat().coerceIn(0f, 1f)
    } else {
        0.5f
    }
    return Offset(
        x = paddingPx + availableWidth * xRatio,
        y = paddingPx + availableHeight * yRatio,
    )
}

private fun CourseOrderRoutePoint.toFallbackMapOffset(
    bounds: CourseOrderCoordinateBounds,
    width: Float,
    height: Float,
    paddingPx: Float,
): Offset {
    val coordinate = CourseOrderMapPlace(
        id = 0L,
        order = 0,
        latitude = latitude,
        longitude = longitude,
    )
    return coordinate.toFallbackMapOffset(
        bounds = bounds,
        width = width,
        height = height,
        paddingPx = paddingPx,
    )
}

private fun disableCourseOrderMapGestures(kakaoMap: KakaoMap) {
    GestureType.values()
        .filterNot { gestureType -> gestureType == GestureType.Unknown }
        .forEach { gestureType ->
            kakaoMap.setGestureEnable(
                gestureType,
                gestureType in CourseOrderAllowedMapGestures,
            )
        }
}

private fun renderCourseOrderMapPins(
    kakaoMap: KakaoMap,
    mapPlaces: List<CourseOrderMapPlace>,
) {
    val labelManager = kakaoMap.labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    layer.setClickable(false)
    mapPlaces.forEach { place ->
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(
                    createCourseOrderPinBitmap(order = place.order),
                ).setAnchorPoint(0.5f, 1f),
            ),
        )
        layer.addLabel(
            LabelOptions.from(
                "course-order-${place.id}",
                place.toLatLng(),
            )
                .setStyles(styles)
                .setRank(place.order.toLong()),
        )
    }
}

private fun renderCourseOrderRouteLine(
    kakaoMap: KakaoMap,
    route: CourseOrderRouteInfo?,
) {
    val layer = kakaoMap.routeLineManager?.layer ?: return
    layer.removeAll()
    val points: List<LatLng> = route?.points
        .orEmpty()
        .map(CourseOrderRoutePoint::toLatLng)
    if (points.size < MinimumRouteStopCount) return
    val styles = RouteLineStyles.from(
        RouteLineStyle.from(
            CourseOrderRouteLineWidth,
            AndroidColor.rgb(255, 207, 84),
            CourseOrderRouteStrokeWidth,
            AndroidColor.WHITE,
        ),
    )
    val stylesSet = RouteLineStylesSet.from(CourseOrderRouteStyleId, styles)
    val segment = RouteLineSegment.from(points, stylesSet.getStyles(0))
    val options = RouteLineOptions
        .from(CourseOrderRouteLineId, segment)
        .setStylesSet(stylesSet)
        .setZOrder(CourseOrderRouteLineZOrder)
    layer.addRouteLine(options)
}

private fun moveCourseOrderMapCamera(
    kakaoMap: KakaoMap,
    mapPlaces: List<CourseOrderMapPlace>,
    fitPaddingPx: Int,
) {
    when (mapPlaces.size) {
        0 -> Unit
        1 -> kakaoMap.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                mapPlaces.first().toLatLng(),
                CourseOrderSinglePlaceMapZoomLevel,
            ),
        )

        else -> kakaoMap.moveCamera(
            CameraUpdateFactory.fitMapPoints(
                mapPlaces.map(CourseOrderMapPlace::toLatLng).toTypedArray(),
                fitPaddingPx,
            ),
        )
    }
}

private fun createCourseOrderPinBitmap(
    order: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(CourseOrderPinBitmapWidth, CourseOrderPinBitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centerX = CourseOrderPinBitmapWidth / 2f
    val circleCenterY = 34f
    val bodyPath = AndroidPath().apply {
        moveTo(centerX - 24f, 54f)
        lineTo(centerX, CourseOrderPinBitmapHeight - 4f)
        lineTo(centerX + 24f, 54f)
        close()
    }
    paint.color = AndroidColor.argb(72, 0, 0, 0)
    canvas.drawCircle(centerX, circleCenterY + 4f, 30f, paint)
    canvas.drawPath(bodyPath, paint)
    paint.color = AndroidColor.rgb(46, 46, 46)
    canvas.drawCircle(centerX, circleCenterY, 30f, paint)
    canvas.drawPath(bodyPath, paint)
    paint.color = AndroidColor.rgb(245, 245, 245)
    canvas.drawCircle(centerX, circleCenterY, 19f, paint)
    paint.color = AndroidColor.rgb(20, 20, 20)
    paint.textSize = if (order >= 10) 21f else 24f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    val baseline = circleCenterY - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(order.toString(), centerX, baseline, paint)
    return bitmap
}

private fun DrawScope.drawCourseOrderFallbackRoute(
    routePoints: List<CourseOrderRoutePoint>,
    bounds: CourseOrderCoordinateBounds,
    width: Float,
    height: Float,
    paddingPx: Float,
) {
    val path = Path()
    routePoints.forEachIndexed { index, point ->
        val offset = point.toFallbackMapOffset(
            bounds = bounds,
            width = width,
            height = height,
            paddingPx = paddingPx,
        )
        if (index == 0) {
            path.moveTo(offset.x, offset.y)
        } else {
            path.lineTo(offset.x, offset.y)
        }
    }
    drawPath(
        path = path,
        color = Color.White,
        style = Stroke(
            width = 16f,
            cap = StrokeCap.Round,
        ),
    )
    drawPath(
        path = path,
        color = Color(0xFFFFCF54),
        style = Stroke(
            width = 10f,
            cap = StrokeCap.Round,
        ),
    )
}

private fun DrawScope.drawCourseOrderFallbackPin(
    anchor: Offset,
    order: Int,
) {
    val pinColor = Color(0xFF2E2E2E)
    val center = Offset(anchor.x, anchor.y - 28f)
    val pointer = Path().apply {
        moveTo(center.x - 17f, center.y + 18f)
        lineTo(anchor.x, anchor.y + 2f)
        lineTo(center.x + 17f, center.y + 18f)
        close()
    }
    drawPath(
        path = pointer,
        color = pinColor,
    )
    drawCircle(
        color = pinColor,
        radius = 27f,
        center = center,
    )
    drawCircle(
        color = Color.White,
        radius = 17f,
        center = center,
    )
    drawCircle(
        color = Color(0x22000000),
        radius = 17f,
        center = center,
        style = Stroke(width = 1.5f),
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(20, 20, 20)
        textSize = if (order >= 10) 20f else 23f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    val baseline = center.y - (paint.descent() + paint.ascent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(order.toString(), center.x, baseline, paint)
}

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

private const val CourseIdArgument: String = "courseId"
private const val DefaultCourseId: Long = 1L
private const val MinimumRouteStopCount: Int = 2
private val CourseOrderDragThreshold = 104.dp
private val CourseOrderCardTravel = 70.dp
private val CourseOrderMapHeight = 246.dp
private val CourseOrderMapFitPadding = 58.dp
private val CourseOrderFallbackMapPadding = 54.dp
private const val DefaultCourseMapLatitude: Double = 35.8347
private const val DefaultCourseMapLongitude: Double = 129.2187
private const val CourseOrderDefaultMapZoomLevel: Int = 12
private const val CourseOrderSinglePlaceMapZoomLevel: Int = 15
private const val CourseOrderPinBitmapWidth: Int = 74
private const val CourseOrderPinBitmapHeight: Int = 92
private const val CourseOrderRouteLineWidth: Float = 13f
private const val CourseOrderRouteStrokeWidth: Float = 5f
private const val CourseOrderRouteLineZOrder: Int = 0
private const val CourseOrderRouteStyleId: String = "course-order-route-style"
private const val CourseOrderRouteLineId: String = "course-order-route-line"
private const val CourseOrderMinimumCoordinateSpan: Double = 0.000001
private const val SecondsPerMinute: Int = 60
private const val MinutesPerHour: Int = 60
private const val MetersPerKilometer: Int = 1000
private val CourseOrderAllowedMapGestures: Set<GestureType> = setOf(
    GestureType.Pan,
    GestureType.Zoom,
    GestureType.RotateZoom,
)
