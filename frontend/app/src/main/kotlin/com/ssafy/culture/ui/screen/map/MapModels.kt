package com.ssafy.culture.ui.screen.map

data class MapUiState(
    val heritageMarkers: List<HeritageMarker> = getDefaultHeritageMarkers(),
    val searchResults: List<KeywordSearchResult> = emptyList(),
    val selectedOverlay: SelectedMarkerOverlay? = null,
    val keywordChips: List<KeywordChip> = getDefaultKeywordChips(),
    val currentCenter: MapCenter = MapCenter(),
    val currentLocation: MapCenter? = null,
    val activeKeyword: String = getDefaultKeywordChips().first().keyword,
    val isSearching: Boolean = false,
    val isFollowingCurrentLocation: Boolean = false,
    val errorMessage: String? = null,
)

data class HeritageMarker(
    val id: Int,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val address: String,
    val summary: String = "",
)

data class KeywordSearchResult(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val address: String,
    val distanceMeters: Int? = null,
    val placeUrl: String = "",
)

data class SelectedMarkerOverlay(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val category: String,
    val address: String,
    val summary: String = "",
    val placeUrl: String = "",
    val source: MarkerSource,
)

enum class MarkerSource {
    Heritage,
    Search,
}

data class KeywordChip(
    val id: String,
    val label: String,
    val keyword: String,
    val isSelected: Boolean = false,
)

data class MapCenter(
    val latitude: Double = DefaultMapLatitude,
    val longitude: Double = DefaultMapLongitude,
    val zoomLevel: Int = DefaultMapZoomLevel,
)

fun HeritageMarker.toSelectedOverlay(): SelectedMarkerOverlay =
    SelectedMarkerOverlay(
        id = "heritage-$id",
        title = title,
        latitude = latitude,
        longitude = longitude,
        category = category,
        address = address,
        summary = summary,
        source = MarkerSource.Heritage,
    )

fun KeywordSearchResult.toSelectedOverlay(): SelectedMarkerOverlay =
    SelectedMarkerOverlay(
        id = "search-$id",
        title = title,
        latitude = latitude,
        longitude = longitude,
        category = category,
        address = address,
        summary = distanceMeters?.let { "${it}m 거리" }.orEmpty(),
        placeUrl = placeUrl,
        source = MarkerSource.Search,
    )

fun getDefaultHeritageMarkers(): List<HeritageMarker> =
    listOf(
        HeritageMarker(
            id = 101,
            title = "첨성대",
            latitude = 35.8347,
            longitude = 129.2190,
            category = "문화재",
            address = "경상북도 경주시 인왕동 839-1",
            summary = "신라 시대 천문 관측을 상징하는 경주의 대표 문화유산",
        ),
        HeritageMarker(
            id = 102,
            title = "대릉원",
            latitude = 35.8397,
            longitude = 129.2102,
            category = "고분",
            address = "경상북도 경주시 황남동",
            summary = "신라 왕릉과 고분이 모여 있는 경주 역사 산책지",
        ),
        HeritageMarker(
            id = 103,
            title = "천마총",
            latitude = 35.8380,
            longitude = 129.2105,
            category = "고분",
            address = "경상북도 경주시 황남동",
            summary = "천마도가 출토된 신라 고분",
        ),
        HeritageMarker(
            id = 104,
            title = "동궁과 월지",
            latitude = 35.8346,
            longitude = 129.2270,
            category = "궁궐터",
            address = "경상북도 경주시 원화로 102",
            summary = "신라 왕궁의 별궁 터와 아름다운 연못",
        ),
        HeritageMarker(
            id = 105,
            title = "불국사",
            latitude = 35.7900,
            longitude = 129.3319,
            category = "사찰",
            address = "경상북도 경주시 불국로 385",
            summary = "유네스코 세계문화유산으로 등재된 대표 사찰",
        ),
    )

fun getDefaultKeywordChips(): List<KeywordChip> =
    listOf(
        KeywordChip(
            id = "heritage",
            label = "문화재",
            keyword = "문화재",
            isSelected = true,
        ),
        KeywordChip(
            id = "museum",
            label = "박물관",
            keyword = "박물관",
        ),
        KeywordChip(
            id = "cafe",
            label = "카페",
            keyword = "카페",
        ),
        KeywordChip(
            id = "food",
            label = "식당",
            keyword = "식당",
        ),
    )

const val DefaultMapLatitude: Double = 35.8347
const val DefaultMapLongitude: Double = 129.2190
const val DefaultMapZoomLevel: Int = 13
