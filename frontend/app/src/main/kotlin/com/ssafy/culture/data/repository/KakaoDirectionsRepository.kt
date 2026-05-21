package com.ssafy.culture.data.repository

import com.ssafy.culture.data.remote.KakaoDirectionRoadDto
import com.ssafy.culture.data.remote.KakaoDirectionRouteDto
import com.ssafy.culture.data.remote.KakaoDirectionsApi
import com.ssafy.culture.data.remote.KakaoLocalAuthHeaderProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface KakaoDirectionsRepository {
    suspend fun getCourseRoute(
        stops: List<KakaoRouteStop>,
    ): KakaoCourseRoute?
}

@Singleton
class DefaultKakaoDirectionsRepository @Inject constructor(
    private val kakaoDirectionsApi: KakaoDirectionsApi,
    private val authHeaderProvider: KakaoLocalAuthHeaderProvider,
) : KakaoDirectionsRepository {
    override suspend fun getCourseRoute(
        stops: List<KakaoRouteStop>,
    ): KakaoCourseRoute? = withContext(Dispatchers.IO) {
        require(stops.size >= MinimumRouteStopCount) {
            "At least two route stops are required."
        }
        val middleStops: List<KakaoRouteStop> = stops.drop(1).dropLast(1)
        val response = kakaoDirectionsApi.getDirections(
            authorization = authHeaderProvider.getAuthorizationHeader(),
            origin = stops.first().toApiCoordinate(),
            destination = stops.last().toApiCoordinate(),
            waypoints = middleStops.toWaypointsQuery(),
        )
        response.routes
            .orEmpty()
            .firstOrNull { route -> route.resultCode == KakaoDirectionsSuccessCode }
            ?.toRepositoryModel()
            ?.takeIf { route -> route.points.size >= MinimumRouteStopCount }
    }

    private fun List<KakaoRouteStop>.toWaypointsQuery(): String? =
        takeIf(List<KakaoRouteStop>::isNotEmpty)
            ?.joinToString(separator = "|") { stop -> stop.toApiCoordinate() }

    private companion object {
        const val MinimumRouteStopCount = 2
        const val KakaoDirectionsSuccessCode = 0
    }
}

data class KakaoRouteStop(
    val latitude: Double,
    val longitude: Double,
)

data class KakaoCourseRoute(
    val distanceMeters: Int,
    val durationSeconds: Int,
    val points: List<KakaoRoutePoint>,
)

data class KakaoRoutePoint(
    val latitude: Double,
    val longitude: Double,
)

private fun KakaoRouteStop.toApiCoordinate(): String =
    "$longitude,$latitude"

private fun KakaoDirectionRouteDto.toRepositoryModel(): KakaoCourseRoute {
    val roadPoints: List<KakaoRoutePoint> = sections
        .orEmpty()
        .flatMap { section -> section.roads.orEmpty() }
        .flatMap(KakaoDirectionRoadDto::toRoutePoints)
    val sectionDistance: Int = sections.orEmpty().sumOf { section -> section.distance ?: 0 }
    val sectionDuration: Int = sections.orEmpty().sumOf { section -> section.duration ?: 0 }
    return KakaoCourseRoute(
        distanceMeters = summary?.distance ?: sectionDistance,
        durationSeconds = summary?.duration ?: sectionDuration,
        points = roadPoints.deduplicateNeighbors(),
    )
}

private fun KakaoDirectionRoadDto.toRoutePoints(): List<KakaoRoutePoint> =
    vertexes
        .orEmpty()
        .chunked(CoordinatePairSize)
        .mapNotNull { pair ->
            val longitude: Double = pair.getOrNull(LongitudeIndex) ?: return@mapNotNull null
            val latitude: Double = pair.getOrNull(LatitudeIndex) ?: return@mapNotNull null
            KakaoRoutePoint(
                latitude = latitude,
                longitude = longitude,
            )
        }

private fun List<KakaoRoutePoint>.deduplicateNeighbors(): List<KakaoRoutePoint> =
    fold(emptyList()) { points, point ->
        if (points.lastOrNull() == point) {
            points
        } else {
            points + point
        }
    }

private const val CoordinatePairSize: Int = 2
private const val LongitudeIndex: Int = 0
private const val LatitudeIndex: Int = 1
