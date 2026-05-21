package com.ssafy.culture.data.story

import com.google.gson.annotations.SerializedName

data class StoryApiResponseDto<T>(
    @SerializedName("success")
    val success: Boolean?,
    @SerializedName("code")
    val code: Int?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: T?,
)

data class StoryListResponseDto(
    @SerializedName("success")
    val success: Boolean?,
    @SerializedName("code")
    val code: Int?,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: List<StorySummaryDto>?,
    @SerializedName("pageInfo")
    val pageInfo: StoryPageInfoDto?,
)

data class StoryCreateRequestDto(
    @SerializedName("courseId")
    val courseId: Long,
    @SerializedName("protagonists")
    val protagonists: List<StoryProtagonistDto>,
    @SerializedName("placeIds")
    val placeIds: List<Long>,
)

data class StoryProtagonistDto(
    @SerializedName("name")
    val name: String?,
    @SerializedName("age")
    val age: Int?,
    @SerializedName("tendency")
    val tendency: String?,
)

data class StorySummaryDto(
    @SerializedName("storyId")
    val storyId: Long?,
    @SerializedName("courseId")
    val courseId: Long?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("thumbnailUrl")
    val thumbnailUrl: String?,
    @SerializedName("completedCount")
    val completedCount: Int?,
    @SerializedName("totalCount")
    val totalCount: Int?,
    @SerializedName("createdAt")
    val createdAt: String?,
)

data class StoryPageInfoDto(
    @SerializedName("page")
    val page: Int?,
    @SerializedName("size")
    val size: Int?,
    @SerializedName("totalElements")
    val totalElements: Int?,
    @SerializedName("totalPages")
    val totalPages: Int?,
)

data class StoryDetailDto(
    @SerializedName("storyId")
    val storyId: Long?,
    @SerializedName("courseId")
    val courseId: Long?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("status")
    val status: String?,
    @SerializedName("intro")
    val intro: String?,
    @SerializedName("outro")
    val outro: String?,
    @SerializedName("protagonists")
    val protagonists: List<StoryProtagonistDto>?,
    @SerializedName("chapters")
    val chapters: List<StoryChapterSummaryDto>?,
    @SerializedName("createdAt")
    val createdAt: String?,
)

data class StoryChapterSummaryDto(
    @SerializedName("chapterId")
    val chapterId: Long?,
    @SerializedName("sequence")
    val sequence: Int?,
    @SerializedName("placeId")
    val placeId: Long?,
    @SerializedName("placeTitle")
    val placeTitle: String?,
    @SerializedName("isCompleted")
    val isCompleted: Boolean?,
)

data class StoryProgressDto(
    @SerializedName("storyId")
    val storyId: Long?,
    @SerializedName("totalCount")
    val totalCount: Int?,
    @SerializedName("completedCount")
    val completedCount: Int?,
    @SerializedName("progressRate")
    val progressRate: Float?,
    @SerializedName("chapters")
    val chapters: List<StoryProgressChapterDto>?,
)

data class StoryProgressChapterDto(
    @SerializedName("chapterId")
    val chapterId: Long?,
    @SerializedName("placeTitle")
    val placeTitle: String?,
    @SerializedName("isCompleted")
    val isCompleted: Boolean?,
    @SerializedName("locationVerificationStatus")
    val locationVerificationStatus: String?,
)

data class StoryChapterDetailDto(
    @SerializedName("chapterId")
    val chapterId: Long?,
    @SerializedName("sequence")
    val sequence: Int?,
    @SerializedName("isCompleted")
    val isCompleted: Boolean?,
    @SerializedName("place")
    val place: StoryPlaceDto?,
    @SerializedName("story")
    val story: StoryContentDto?,
    @SerializedName("mission")
    val mission: StoryMissionDto?,
)

data class StoryPlaceDto(
    @SerializedName("placeId")
    val placeId: Long?,
    @SerializedName("title")
    val title: String?,
    @SerializedName("latitude")
    val latitude: Double?,
    @SerializedName("longitude")
    val longitude: Double?,
)

data class StoryContentDto(
    @SerializedName("content")
    val content: String?,
)

data class StoryMissionDto(
    @SerializedName("title")
    val title: String?,
    @SerializedName("description")
    val description: String?,
    @SerializedName(value = "verificationHint", alternate = ["verification_hint"])
    val verificationHint: String?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("progress")
    val progress: StoryMissionProgressDto?,
)

data class StoryMissionProgressDto(
    @SerializedName("isCompleted")
    val isCompleted: Boolean?,
    @SerializedName("fileUrl")
    val fileUrl: String?,
    @SerializedName("locationVerificationStatus")
    val locationVerificationStatus: String?,
    @SerializedName("completedAt")
    val completedAt: String?,
)

data class MissionSubmitRequestDto(
    @SerializedName("result")
    val result: MissionResultDto,
)

data class MissionResultDto(
    @SerializedName("isCompleted")
    val isCompleted: Boolean,
)

data class MissionSubmitResultDto(
    @SerializedName("storyId")
    val storyId: Long?,
    @SerializedName("chapterId")
    val chapterId: Long?,
    @SerializedName("isCompleted")
    val isCompleted: Boolean?,
    @SerializedName("completedAt")
    val completedAt: String?,
    @SerializedName("nextChapterId")
    val nextChapterId: Long?,
)

data class MissionFileSubmitRequestDto(
    @SerializedName("fileUrl")
    val fileUrl: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("locationVerificationStatus")
    val locationVerificationStatus: String? = null,
)

data class MissionFileSubmitResultDto(
    @SerializedName("storyId")
    val storyId: Long?,
    @SerializedName("chapterId")
    val chapterId: Long?,
    @SerializedName("fileUrl")
    val fileUrl: String?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("locationVerificationStatus")
    val locationVerificationStatus: String?,
    @SerializedName("uploadedAt")
    val uploadedAt: String?,
)
