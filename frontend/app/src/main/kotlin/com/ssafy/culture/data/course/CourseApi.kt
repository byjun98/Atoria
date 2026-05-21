package com.ssafy.culture.data.course

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CourseApi {
    @GET("courses")
    suspend fun getCourses(): CourseApiResponse<List<CourseListItemDto>>

    @GET("courses/{courseId}")
    suspend fun getCourseDetail(
        @Path("courseId") courseId: Long,
    ): CourseApiResponse<CourseDetailDto>

    @GET("places")
    suspend fun getPlaces(
        @Query("category") category: String? = null,
        @Query("keyword") keyword: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): CourseApiResponse<List<PlaceListItemDto>>

    @GET("places/{placeId}")
    suspend fun getPlaceDetail(
        @Path("placeId") placeId: Long,
    ): CourseApiResponse<PlaceDetailDto>
}
