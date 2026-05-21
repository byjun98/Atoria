package com.ssafy.culture.data.course

data class CourseApiResponse<T>(
    val success: Boolean?,
    val code: Int?,
    val message: String?,
    val data: T?,
    val pageInfo: CoursePageInfoDto?,
    val error: String?,
)

data class CoursePageInfoDto(
    val page: Int?,
    val size: Int?,
    val totalElements: Int?,
    val totalPages: Int?,
)

data class CourseListItemDto(
    val courseId: Long?,
    val title: String?,
    val description: String?,
    val places: List<CoursePlaceSummaryDto>?,
)

data class CourseDetailDto(
    val courseId: Long?,
    val title: String?,
    val description: String?,
    val places: List<CoursePlaceDto>?,
)

data class CoursePlaceSummaryDto(
    val placeId: Long?,
    val title: String?,
)

data class CoursePlaceDto(
    val placeId: Long?,
    val title: String?,
    val latitude: Double?,
    val longitude: Double?,
)

data class PlaceListItemDto(
    val placeId: Long?,
    val title: String?,
    val latitude: Double?,
    val longitude: Double?,
    val thumbnailUrl: String?,
)

data class PlaceDetailDto(
    val placeId: Long?,
    val title: String?,
    val latitude: Double?,
    val longitude: Double?,
    val description: String?,
    val address: String?,
    val category: String?,
    val thumbnailUrl: String?,
)
