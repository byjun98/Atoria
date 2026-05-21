package com.ssafy.culture.data.story

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface StoryApi {
    @POST("stories")
    suspend fun createStory(
        @Body request: StoryCreateRequestDto,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<StoryDetailDto>

    @GET("stories")
    suspend fun getMyStories(
        @Header("Authorization") authorization: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): StoryListResponseDto

    @GET("stories/{storyId}")
    suspend fun getStoryDetail(
        @Path("storyId") storyId: Long,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<StoryDetailDto>

    @GET("stories/{storyId}/chapters/{chapterId}")
    suspend fun getChapterDetail(
        @Path("storyId") storyId: Long,
        @Path("chapterId") chapterId: Long,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<StoryChapterDetailDto>

    @GET("stories/{storyId}/progress")
    suspend fun getStoryProgress(
        @Path("storyId") storyId: Long,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<StoryProgressDto>

    @POST("stories/{storyId}/chapters/{chapterId}/submit")
    suspend fun submitMission(
        @Path("storyId") storyId: Long,
        @Path("chapterId") chapterId: Long,
        @Body request: MissionSubmitRequestDto,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<MissionSubmitResultDto>

    @POST("stories/{storyId}/chapters/{chapterId}/files")
    suspend fun submitMissionFile(
        @Path("storyId") storyId: Long,
        @Path("chapterId") chapterId: Long,
        @Body request: MissionFileSubmitRequestDto,
        @Header("Authorization") authorization: String? = null,
    ): StoryApiResponseDto<MissionFileSubmitResultDto>
}
