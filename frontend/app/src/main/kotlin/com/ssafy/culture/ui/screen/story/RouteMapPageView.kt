package com.ssafy.culture.ui.screen.story

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.ssafy.culture.domain.model.RoutePoint

@Composable
internal fun RouteMapPage(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteMapHeader(
                title = page.title.ifBlank { "다녀온 길" },
                chapterCount = page.routeChapterMarkers.size,
                routePointCount = page.routeUserPoints.size,
            )
            if (page.routeChapterMarkers.isEmpty()) {
                RouteMapEmpty(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
            } else {
                RouteMapKakaoView(
                    markers = page.routeChapterMarkers,
                    userPoints = page.routeUserPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            RouteMapFooter(markers = page.routeChapterMarkers)
        }
    }
}

@Composable
internal fun StaticRouteMapPage(
    page: StoryBookPage,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RouteMapHeader(
                title = page.title.ifBlank { "Route" },
                chapterCount = page.routeChapterMarkers.size,
                routePointCount = page.routeUserPoints.size,
            )
            if (page.routeChapterMarkers.isEmpty()) {
                RouteMapEmpty(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                RouteMapStaticView(
                    markers = page.routeChapterMarkers,
                    userPoints = page.routeUserPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp)),
                )
            }
            RouteMapFooter(markers = page.routeChapterMarkers)
        }
    }
}

@Composable
private fun RouteMapHeader(
    title: String,
    chapterCount: Int,
    routePointCount: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "방문한 문화재 ${chapterCount}곳 · 기록된 좌표 ${routePointCount}개",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RouteMapEmpty(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(Color(0xFFFFF6DF), shape = RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "다녀온 문화재 정보를 불러오지 못했어요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RouteMapFooter(
    markers: List<RouteChapterMarker>,
) {
    if (markers.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFFF6DF),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            markers.forEach { marker ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(22.dp),
                        shape = CircleShape,
                        color = Color(0xFF2E2E2E),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = marker.order.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    Text(
                        text = marker.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    Spacer(modifier = Modifier.size(0.dp))
}

@Composable
private fun RouteMapStaticView(
    markers: List<RouteChapterMarker>,
    userPoints: List<RoutePoint>,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.background(Color(0xFFFFF6DF)),
    ) {
        val markerCoordinates: List<RouteMapCoordinate> = markers.map { marker ->
            RouteMapCoordinate(
                latitude = marker.latitude,
                longitude = marker.longitude,
            )
        }
        val userCoordinates: List<RouteMapCoordinate> = userPoints.map { point ->
            RouteMapCoordinate(
                latitude = point.latitude,
                longitude = point.longitude,
            )
        }
        val allCoordinates: List<RouteMapCoordinate> = markerCoordinates + userCoordinates
        if (allCoordinates.isEmpty()) return@Canvas
        val minLatitude: Double = allCoordinates.minOf(RouteMapCoordinate::latitude)
        val maxLatitude: Double = allCoordinates.maxOf(RouteMapCoordinate::latitude)
        val minLongitude: Double = allCoordinates.minOf(RouteMapCoordinate::longitude)
        val maxLongitude: Double = allCoordinates.maxOf(RouteMapCoordinate::longitude)
        val latitudeCenter: Double = (minLatitude + maxLatitude) / 2.0
        val longitudeCenter: Double = (minLongitude + maxLongitude) / 2.0
        val latitudeSpan: Double = max(maxLatitude - minLatitude, MinimumStaticMapSpan)
        val longitudeSpan: Double = max(maxLongitude - minLongitude, MinimumStaticMapSpan)
        val latitudeBottom: Double = latitudeCenter - latitudeSpan / 2.0
        val longitudeLeft: Double = longitudeCenter - longitudeSpan / 2.0
        val contentPadding: Float = 34.dp.toPx()
        val contentWidth: Float = (size.width - contentPadding * 2f).coerceAtLeast(1f)
        val contentHeight: Float = (size.height - contentPadding * 2f).coerceAtLeast(1f)
        fun project(coordinate: RouteMapCoordinate): Offset {
            val xRatio: Float = ((coordinate.longitude - longitudeLeft) / longitudeSpan)
                .toFloat()
                .coerceIn(0f, 1f)
            val yRatio: Float = (1.0 - (coordinate.latitude - latitudeBottom) / latitudeSpan)
                .toFloat()
                .coerceIn(0f, 1f)
            return Offset(
                x = contentPadding + xRatio * contentWidth,
                y = contentPadding + yRatio * contentHeight,
            )
        }
        repeat(6) { index ->
            val x: Float = size.width * (index + 1) / 7f
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            val y: Float = size.height * (index + 1) / 7f
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
        val routeOffsets: List<Offset> = if (userCoordinates.size >= 2) {
            userCoordinates.map(::project)
        } else {
            markerCoordinates.map(::project)
        }
        routeOffsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = Color.White,
                start = start,
                end = end,
                strokeWidth = 12.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color(0xFFE94A7B),
                start = start,
                end = end,
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = 13.dp.toPx()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        markers.forEach { marker ->
            val offset: Offset = project(
                RouteMapCoordinate(
                    latitude = marker.latitude,
                    longitude = marker.longitude,
                ),
            )
            val radius: Float = 15.dp.toPx()
            drawCircle(
                color = Color.White,
                radius = radius + 4.dp.toPx(),
                center = offset,
                style = Stroke(width = 4.dp.toPx()),
            )
            drawCircle(
                color = Color(0xFF2E2E2E),
                radius = radius,
                center = offset,
            )
            drawContext.canvas.nativeCanvas.drawText(
                marker.order.toString(),
                offset.x,
                offset.y - (textPaint.descent() + textPaint.ascent()) / 2f,
                textPaint,
            )
        }
    }
}

@Composable
private fun RouteMapKakaoView(
    markers: List<RouteChapterMarker>,
    userPoints: List<RoutePoint>,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val fitPaddingPx: Int = with(LocalDensity.current) { 56.dp.roundToPx() }
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
                        disableRouteMapGestures(readyMap)
                        kakaoMap = readyMap
                    }

                    override fun getPosition(): LatLng =
                        markers.firstOrNull()?.let { marker ->
                            LatLng.from(marker.latitude, marker.longitude)
                        } ?: LatLng.from(DefaultLatitude, DefaultLongitude)

                    override fun getZoomLevel(): Int = DefaultMapZoomLevel
                },
            )
        }
    }
    LaunchedEffect(kakaoMap, markers, userPoints, fitPaddingPx) {
        kakaoMap?.let { readyMap ->
            renderRoutePolyline(readyMap, userPoints)
            renderRouteMarkers(readyMap, markers)
            fitRouteCamera(readyMap, markers, userPoints, fitPaddingPx)
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

private fun disableRouteMapGestures(kakaoMap: KakaoMap) {
    val allowedGestures: Set<GestureType> = setOf(
        GestureType.Pan,
        GestureType.Zoom,
        GestureType.RotateZoom,
    )
    GestureType.values()
        .filterNot { gestureType -> gestureType == GestureType.Unknown }
        .forEach { gestureType ->
            kakaoMap.setGestureEnable(gestureType, gestureType in allowedGestures)
        }
}

private fun renderRouteMarkers(
    kakaoMap: KakaoMap,
    markers: List<RouteChapterMarker>,
) {
    val labelManager = kakaoMap.labelManager ?: return
    val layer = labelManager.layer ?: return
    layer.removeAll()
    layer.setClickable(false)
    markers.forEachIndexed { index, marker ->
        val styles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from(createRouteMarkerBitmap(order = marker.order))
                    .setAnchorPoint(0.5f, 1f),
            ),
        )
        layer.addLabel(
            LabelOptions.from(
                "route-marker-${marker.placeId}-$index",
                LatLng.from(marker.latitude, marker.longitude),
            )
                .setStyles(styles)
                .setRank(marker.order.toLong()),
        )
    }
}

private fun renderRoutePolyline(
    kakaoMap: KakaoMap,
    userPoints: List<RoutePoint>,
) {
    val layer = kakaoMap.routeLineManager?.layer ?: return
    layer.removeAll()
    val points: List<LatLng> = userPoints.map { point ->
        LatLng.from(point.latitude, point.longitude)
    }
    if (points.size < 2) return
    val styles = RouteLineStyles.from(
        RouteLineStyle.from(
            RoutePolylineWidth,
            AndroidColor.rgb(233, 74, 123),
            RoutePolylineStrokeWidth,
            AndroidColor.WHITE,
        ),
    )
    val stylesSet = RouteLineStylesSet.from(RoutePolylineStyleId, styles)
    val segment = RouteLineSegment.from(points, stylesSet.getStyles(0))
    val options = RouteLineOptions
        .from(RoutePolylineId, segment)
        .setStylesSet(stylesSet)
        .setZOrder(0)
    layer.addRouteLine(options)
}

private fun fitRouteCamera(
    kakaoMap: KakaoMap,
    markers: List<RouteChapterMarker>,
    userPoints: List<RoutePoint>,
    fitPaddingPx: Int,
) {
    val allPoints: List<LatLng> = buildList {
        markers.forEach { marker -> add(LatLng.from(marker.latitude, marker.longitude)) }
        userPoints.forEach { point -> add(LatLng.from(point.latitude, point.longitude)) }
    }
    when {
        allPoints.isEmpty() -> Unit
        allPoints.size == 1 -> kakaoMap.moveCamera(
            CameraUpdateFactory.newCenterPosition(allPoints.first(), SinglePointMapZoomLevel),
        )
        else -> kakaoMap.moveCamera(
            CameraUpdateFactory.fitMapPoints(allPoints.toTypedArray(), fitPaddingPx),
        )
    }
}

private fun createRouteMarkerBitmap(order: Int): Bitmap {
    val width = 74
    val height = 92
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val centerX = width / 2f
    val circleCenterY = 34f
    val bodyPath = AndroidPath().apply {
        moveTo(centerX - 24f, 54f)
        lineTo(centerX, height - 4f)
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

private data class RouteMapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

private const val DefaultLatitude: Double = 35.8347
private const val DefaultLongitude: Double = 129.2187
private const val DefaultMapZoomLevel: Int = 12
private const val SinglePointMapZoomLevel: Int = 15
private const val RoutePolylineWidth: Float = 11f
private const val RoutePolylineStrokeWidth: Float = 4f
private const val RoutePolylineStyleId: String = "route-map-polyline-style"
private const val RoutePolylineId: String = "route-map-polyline"
private const val MinimumStaticMapSpan: Double = 0.008
