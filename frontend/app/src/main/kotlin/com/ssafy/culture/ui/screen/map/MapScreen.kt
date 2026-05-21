package com.ssafy.culture.ui.screen.map

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.ui.component.MainBottomBar
import com.ssafy.culture.ui.component.MainDestination
import com.ssafy.culture.ui.motion.tossClickable

@Composable
fun MapRoute(
    onOpenHome: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    MapScreen(
        uiState = uiState,
        onOpenHome = onOpenHome,
        onOpenStory = onOpenStory,
        onOpenProfile = onOpenProfile,
        onMapReady = viewModel::loadInitialSearch,
        onMapCenterChanged = { center, isUserGesture ->
            viewModel.updateMapCenter(center, isUserGesture)
        },
        onKeywordClick = viewModel::searchByKeyword,
        onResearchHere = viewModel::researchAtCurrentCenter,
        onCurrentLocationResolved = viewModel::setCurrentLocation,
        onOverlaySelected = viewModel::selectOverlay,
        onOverlayClosed = viewModel::clearSelectedOverlay,
    )
}

@Composable
private fun MapScreen(
    uiState: MapUiState,
    onOpenHome: () -> Unit,
    onOpenStory: () -> Unit,
    onOpenProfile: () -> Unit,
    onMapReady: () -> Unit,
    onMapCenterChanged: (MapCenter, Boolean) -> Unit,
    onKeywordClick: (String) -> Unit,
    onResearchHere: () -> Unit,
    onCurrentLocationResolved: (MapCenter) -> Unit,
    onOverlaySelected: (SelectedMarkerOverlay?) -> Unit,
    onOverlayClosed: () -> Unit,
) {
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    var isMapLoading by remember { mutableStateOf(true) }
    var mapErrorMessage by remember { mutableStateOf<String?>(null) }
    var detailWebUrl by remember { mutableStateOf<String?>(null) }
    val moveCameraTo: (MapCenter) -> Unit = { center ->
        kakaoMap?.moveCamera(
            CameraUpdateFactory.newCenterPosition(
                LatLng.from(center.latitude, center.longitude),
                center.zoomLevel,
            ),
        )
    }
    val moveCameraToOverlay: (SelectedMarkerOverlay) -> Unit = { overlay ->
        moveCameraTo(
            MapCenter(
                latitude = overlay.latitude,
                longitude = overlay.longitude,
                zoomLevel = uiState.currentCenter.zoomLevel.coerceAtLeast(SelectedMarkerZoomLevel),
            ),
        )
    }
    val handleCurrentLocation: (Location?) -> Unit = { location ->
        val center = location?.toMapCenter()
        if (center == null) {
            mapErrorMessage = CurrentLocationUnavailableMessage
        } else {
            mapErrorMessage = null
            onCurrentLocationResolved(center)
            moveCameraTo(center)
        }
    }
    val resolveCurrentLocation: () -> Unit = {
        mapErrorMessage = null
        requestCurrentLocation(
            context = context,
            onResult = handleCurrentLocation,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { isGranted -> isGranted }) {
            resolveCurrentLocation()
        } else {
            mapErrorMessage = LocationPermissionRequiredMessage
        }
    }
    LaunchedEffect(kakaoMap) {
        if (kakaoMap != null) {
            onMapReady()
        }
    }
    Scaffold(
        containerColor = Color(0xFFF7F1EA),
        bottomBar = {
            MainBottomBar(
                selectedDestination = MainDestination.Map,
                onHomeClick = onOpenHome,
                onMapClick = {},
                onStoryClick = onOpenStory,
                onProfileClick = onOpenProfile,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE8E1D7)),
        ) {
            KakaoMapViewport(
                heritageMarkers = uiState.heritageMarkers,
                searchResults = uiState.searchResults,
                currentLocation = uiState.currentLocation,
                modifier = Modifier.fillMaxSize(),
                onReady = { readyMap ->
                    kakaoMap = readyMap
                    isMapLoading = false
                    mapErrorMessage = null
                },
                onError = { message ->
                    isMapLoading = false
                    mapErrorMessage = message
                },
                onMarkerSelected = { overlay ->
                    onOverlaySelected(overlay)
                },
                onMapTapped = onOverlayClosed,
                onCameraMoveEnd = onMapCenterChanged,
            )
            MapControls(
                keywords = uiState.keywordChips.map { chip ->
                    MapKeywordButtonState(
                        id = chip.id,
                        label = chip.label,
                        isSelected = chip.isSelected,
                        isEnabled = !uiState.isSearching,
                    )
                },
                onKeywordClick = onKeywordClick,
                onZoomInClick = {
                    kakaoMap?.moveCamera(CameraUpdateFactory.zoomIn())
                },
                onZoomOutClick = {
                    kakaoMap?.moveCamera(CameraUpdateFactory.zoomOut())
                },
                onCurrentLocationClick = {
                    if (hasLocationPermission(context)) {
                        resolveCurrentLocation()
                    } else {
                        permissionLauncher.launch(LocationPermissions)
                    }
                },
                onResearchHereClick = onResearchHere,
                modifier = Modifier
                    .matchParentSize()
                    .padding(top = 16.dp, end = 16.dp, bottom = 24.dp),
                isResearchHereEnabled = !uiState.isSearching,
                isFollowingCurrentLocation = uiState.isFollowingCurrentLocation,
            )
            uiState.selectedOverlay?.let { overlay ->
                SelectedMarkerCard(
                    overlay = overlay,
                    onClose = onOverlayClosed,
                    onOpenDetail = {
                        detailWebUrl = overlay.toKakaoPlaceDetailUrl()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                )
            }
            detailWebUrl?.let { url ->
                KakaoPlaceDetailModal(
                    url = url,
                    onClose = { detailWebUrl = null },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (isMapLoading) {
                MapLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
            val message = mapErrorMessage ?: uiState.errorMessage
            if (message != null) {
                MapErrorBanner(
                    message = message,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(start = 16.dp, top = 72.dp, end = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun KakaoMapViewport(
    heritageMarkers: List<HeritageMarker>,
    searchResults: List<KeywordSearchResult>,
    currentLocation: MapCenter?,
    onReady: (KakaoMap) -> Unit,
    onError: (String) -> Unit,
    onMarkerSelected: (SelectedMarkerOverlay?) -> Unit,
    onMapTapped: () -> Unit,
    onCameraMoveEnd: (MapCenter, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) {
        LaunchedEffect(Unit) {
            onError("Kakao native app key is not configured.")
        }
        Box(modifier = modifier)
        return
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    val latestOnReady by rememberUpdatedState(onReady)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnMarkerSelected by rememberUpdatedState(onMarkerSelected)
    val latestOnMapTapped by rememberUpdatedState(onMapTapped)
    val latestOnCameraMoveEnd by rememberUpdatedState(onCameraMoveEnd)
    val mapView = remember {
        MapView(context).also { view ->
            view.start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit

                    override fun onMapError(error: Exception?) {
                        latestOnError(error?.localizedMessage ?: "Failed to load Kakao map.")
                    }
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(readyMap: KakaoMap) {
                        readyMap.setOnMapClickListener { _, _, _, _ ->
                            latestOnMapTapped()
                        }
                        readyMap.setOnLabelClickListener { _, _, label ->
                            val overlay = label.tag as? SelectedMarkerOverlay
                            latestOnMarkerSelected(overlay)
                            overlay != null
                        }
                        readyMap.setOnCameraMoveEndListener { _, cameraPosition, gestureType ->
                            val gestureName = runCatching { gestureType?.toString().orEmpty() }
                                .getOrDefault("")
                            val isUserGesture = gestureName.isNotEmpty() &&
                                !gestureName.equals("Unknown", ignoreCase = true)
                            latestOnCameraMoveEnd(cameraPosition.toMapCenter(), isUserGesture)
                        }
                        kakaoMap = readyMap
                        latestOnReady(readyMap)
                    }

                    override fun getPosition(): LatLng =
                        LatLng.from(DefaultMapLatitude, DefaultMapLongitude)

                    override fun getZoomLevel(): Int = DefaultMapZoomLevel
                },
            )
        }
    }
    LaunchedEffect(kakaoMap, heritageMarkers, searchResults, currentLocation) {
        kakaoMap?.let { readyMap ->
            renderMapLabels(
                context = context,
                kakaoMap = readyMap,
                heritageMarkers = heritageMarkers,
                searchResults = searchResults,
                currentLocation = currentLocation,
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
    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )
}

@Composable
private fun SelectedMarkerCard(
    overlay: SelectedMarkerOverlay,
    onClose: () -> Unit,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .tossClickable(onClick = onOpenDetail),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, top = 14.dp, end = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overlay.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = overlay.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = overlay.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (overlay.summary.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = overlay.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "닫기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun KakaoPlaceDetailModal(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .padding(horizontal = 14.dp, vertical = 34.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            shape = RoundedCornerShape(26.dp),
            color = Color.White,
            shadowElevation = 18.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, top = 12.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "카카오맵 상세",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "닫기",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    factory = { context ->
                        WebView(context).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        if (webView.url != url) {
                            webView.loadUrl(url)
                        }
                    },
                )
            }
        }
    }
}

private fun SelectedMarkerOverlay.toKakaoPlaceDetailUrl(): String =
    placeUrl.ifBlank {
        "https://map.kakao.com/link/search/${Uri.encode("$title $address")}"
    }

@Composable
private fun MapLoadingIndicator(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "지도를 불러오는 중",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun MapErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shadowElevation = 8.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

private fun renderMapLabels(
    context: Context,
    kakaoMap: KakaoMap,
    heritageMarkers: List<HeritageMarker>,
    searchResults: List<KeywordSearchResult>,
    currentLocation: MapCenter?,
) {
    val labelManager = kakaoMap.labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    layer.setClickable(true)
    val visibleHeritageMarkers: List<HeritageMarker> = if (searchResults.isEmpty()) heritageMarkers else emptyList()
    val visibleSearchResults: List<KeywordSearchResult> = searchResults.deduplicateSearchResults()
    val currentLocationStyles = labelManager.addLabelStyles(
        LabelStyles.from(
            LabelStyle.from(createCurrentLocationBitmap())
                .setAnchorPoint(0.5f, 0.5f),
        ),
    )
    visibleHeritageMarkers.forEachIndexed { index, marker ->
        val markerStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(
                    createMarkerBitmap(
                        pinColor = AndroidColor.rgb(233, 74, 123),
                        dotColor = AndroidColor.rgb(233, 74, 123),
                    ),
                ).setAnchorPoint(0.5f, 0.5f),
            ),
        )
        layer.addLabel(
            LabelOptions.from(
                "heritage-${marker.id}",
                LatLng.from(marker.latitude, marker.longitude),
            )
                .setStyles(markerStyles)
                .setClickable(true)
                .setTag(marker.toSelectedOverlay())
                .setRank(index.toLong()),
        )
    }
    visibleSearchResults.forEachIndexed { index, result ->
        val markerStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(
                    createMarkerBitmap(
                        pinColor = AndroidColor.rgb(233, 74, 123),
                        dotColor = AndroidColor.rgb(233, 74, 123),
                    ),
                ).setAnchorPoint(0.5f, 0.5f),
            ),
        )
        layer.addLabel(
            LabelOptions.from(
                "search-${result.id}",
                LatLng.from(result.latitude, result.longitude),
            )
                .setStyles(markerStyles)
                .setClickable(true)
                .setTag(result.toSelectedOverlay())
                .setRank((index + SearchLabelRankOffset).toLong()),
        )
    }
    currentLocation?.let { center ->
        layer.addLabel(
            LabelOptions.from(
                "current-location",
                LatLng.from(center.latitude, center.longitude),
            )
                .setStyles(currentLocationStyles)
                .setClickable(false)
                .setRank(CurrentLocationRank),
        )
    }
}

private fun List<KeywordSearchResult>.deduplicateSearchResults(): List<KeywordSearchResult> =
    distinctBy { result ->
        result.id.ifBlank {
            "${result.title}-${result.latitude.roundForMarkerKey()}-${result.longitude.roundForMarkerKey()}"
        }
    }

private fun Double.roundForMarkerKey(): String =
    "%.5f".format(this)

private fun createMarkerBitmap(
    pinColor: Int,
    dotColor: Int,
): Bitmap {
    val bitmap = Bitmap.createBitmap(MarkerBitmapSize, MarkerBitmapSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = MarkerBitmapCenter
    paint.color = AndroidColor.argb(42, 0, 0, 0)
    canvas.drawCircle(center, center + 3f, 30f, paint)
    paint.color = pinColor
    canvas.drawCircle(center, center, 29f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawPath(createInnerPinPath(centerX = center, centerY = center - 1f), paint)
    paint.color = dotColor
    canvas.drawCircle(center, center - 6f, 4.2f, paint)
    return bitmap
}

private fun createInnerPinPath(
    centerX: Float,
    centerY: Float,
): Path =
    Path().apply {
        moveTo(centerX, centerY + 19f)
        cubicTo(
            centerX - 17f,
            centerY + 2f,
            centerX - 14f,
            centerY - 17f,
            centerX,
            centerY - 17f,
        )
        cubicTo(
            centerX + 14f,
            centerY - 17f,
            centerX + 17f,
            centerY + 2f,
            centerX,
            centerY + 19f,
        )
        close()
    }

private fun createCurrentLocationBitmap(): Bitmap {
    val bitmap = Bitmap.createBitmap(MarkerBitmapSize, MarkerBitmapSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(MarkerBitmapCenter, MarkerBitmapCenter, 31f, paint)
    paint.color = AndroidColor.rgb(74, 144, 226)
    canvas.drawCircle(MarkerBitmapCenter, MarkerBitmapCenter, 24f, paint)
    paint.color = AndroidColor.WHITE
    canvas.drawCircle(MarkerBitmapCenter, MarkerBitmapCenter, 9f, paint)
    return bitmap
}

private fun CameraPosition.toMapCenter(): MapCenter =
    MapCenter(
        latitude = position.latitude,
        longitude = position.longitude,
        zoomLevel = zoomLevel,
    )

private fun Location.toMapCenter(): MapCenter =
    MapCenter(
        latitude = latitude,
        longitude = longitude,
        zoomLevel = SelectedMarkerZoomLevel,
    )

private fun hasLocationPermission(context: Context): Boolean =
    LocationPermissions.any { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

@SuppressLint("MissingPermission")
private fun requestCurrentLocation(
    context: Context,
    onResult: (Location?) -> Unit,
) {
    if (!hasLocationPermission(context)) {
        onResult(null)
        return
    }
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager == null) {
        onResult(null)
        return
    }
    if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
        onResult(null)
        return
    }
    val provider = selectCurrentLocationProvider(context, locationManager)
    if (provider == null) {
        onResult(null)
        return
    }
    val handler = Handler(Looper.getMainLooper())
    val cancellationSignal = CancellationSignal()
    var isResolved = false
    fun resolve(location: Location?) {
        if (isResolved) return
        isResolved = true
        onResult(location?.takeIf(Location::isRecent) ?: resolveRecentKnownLocation(context, locationManager))
    }
    val timeoutRunnable = Runnable {
        cancellationSignal.cancel()
        resolve(null)
    }
    handler.postDelayed(timeoutRunnable, CurrentLocationTimeoutMillis)
    runCatching {
        LocationManagerCompat.getCurrentLocation(
            locationManager,
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
            Consumer { location ->
                handler.removeCallbacks(timeoutRunnable)
                resolve(location)
            },
        )
    }.onFailure {
        handler.removeCallbacks(timeoutRunnable)
        resolve(null)
    }
}

private fun selectCurrentLocationProvider(
    context: Context,
    locationManager: LocationManager,
): String? {
    val enabledProviders = runCatching {
        locationManager.getProviders(true)
    }.getOrDefault(emptyList())
    if (enabledProviders.isEmpty()) return null
    val hasFineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val preferredProviders = if (hasFineLocation) {
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

@SuppressLint("MissingPermission")
private fun resolveRecentKnownLocation(
    context: Context,
    locationManager: LocationManager,
): Location? {
    if (!hasLocationPermission(context)) return null
    return runCatching {
        locationManager.getProviders(true)
            .mapNotNull { provider ->
                runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            }
            .filter(Location::isRecent)
            .maxByOrNull(Location::getTime)
    }.getOrNull()
}

private fun Location.isRecent(): Boolean {
    val ageMillis = System.currentTimeMillis() - time
    return time > 0L && ageMillis in 0..RecentLocationMaxAgeMillis
}

private val LocationPermissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private const val CurrentLocationUnavailableMessage = "현재 위치를 찾을 수 없어요. 위치 서비스와 GPS를 켠 뒤 다시 시도해 주세요."
private const val LocationPermissionRequiredMessage = "현재 위치를 보려면 위치 권한이 필요해요."
private const val CurrentLocationTimeoutMillis = 10000L
private const val RecentLocationMaxAgeMillis = 120000L
private const val SelectedMarkerZoomLevel = 15
private const val SearchLabelRankOffset = 100
private const val CurrentLocationRank = 1000L
private const val MarkerBitmapSize = 80
private const val MarkerBitmapCenter = MarkerBitmapSize / 2f
