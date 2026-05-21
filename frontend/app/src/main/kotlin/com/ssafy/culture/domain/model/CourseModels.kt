package com.ssafy.culture.domain.model

data class CourseSummary(
    val id: Long,
    val title: String,
    val description: String,
    val places: List<CoursePlaceSummary>,
)

data class CourseDetail(
    val id: Long,
    val title: String,
    val description: String,
    val places: List<CoursePlace>,
)

data class CoursePlaceSummary(
    val id: Long,
    val title: String,
)

data class CoursePlace(
    val id: Long,
    val title: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class CourseSelectionDraft(
    val courseId: Long,
    val placeIds: List<Long>,
)

data class PlaceListResult(
    val places: List<PlaceSummary>,
    val pageInfo: CoursePageInfo?,
)

data class PlaceSummary(
    val id: Long,
    val title: String,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailUrl: String?,
)

data class PlaceDetail(
    val id: Long,
    val title: String,
    val latitude: Double?,
    val longitude: Double?,
    val description: String,
    val address: String,
    val category: String,
    val thumbnailUrl: String?,
)

data class CoursePageInfo(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
)
