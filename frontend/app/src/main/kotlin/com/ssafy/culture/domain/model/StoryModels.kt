package com.ssafy.culture.domain.model

data class StoryCreateRequest(
    val courseId: Long,
    val protagonists: List<StoryProtagonist>,
    val placeIds: List<Long>,
)

data class StoryProtagonist(
    val name: String,
    val age: Int,
    val tendency: String,
)

data class StoryListResult(
    val stories: List<StorySummary>,
    val pageInfo: StoryPageInfo?,
)

data class StoryPageInfo(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
)

data class StorySummary(
    val storyId: Long,
    val courseId: Long,
    val title: String,
    val status: StoryStatus,
    val thumbnailUrl: String?,
    val completedCount: Int,
    val totalCount: Int,
    val createdAt: String,
)

data class StoryDetail(
    val storyId: Long,
    val courseId: Long,
    val title: String,
    val status: StoryStatus,
    val intro: String?,
    val outro: String?,
    val protagonists: List<StoryProtagonist>,
    val chapters: List<StoryChapterSummary>,
    val createdAt: String,
)

data class StoryChapterSummary(
    val chapterId: Long,
    val sequence: Int,
    val placeId: Long?,
    val placeTitle: String,
    val isCompleted: Boolean,
)

data class StoryProgress(
    val storyId: Long,
    val totalCount: Int,
    val completedCount: Int,
    val progressRate: Float,
    val chapters: List<StoryProgressChapter>,
)

data class StoryProgressChapter(
    val chapterId: Long,
    val placeTitle: String,
    val isCompleted: Boolean,
    val locationVerificationStatus: String? = null,
)

data class StoryChapterDetail(
    val chapterId: Long,
    val sequence: Int,
    val isCompleted: Boolean,
    val place: StoryPlace,
    val storyContent: StoryContent,
    val mission: StoryMission,
)

data class StoryPlace(
    val placeId: Long,
    val title: String,
    val latitude: Double?,
    val longitude: Double?,
)

data class StoryContent(
    val content: String?,
)

data class StoryMission(
    val title: String,
    val description: String,
    val verificationHint: String,
    val type: StoryMissionType,
    val progress: StoryMissionProgress,
)

data class StoryMissionProgress(
    val isCompleted: Boolean,
    val fileUrl: String?,
    val completedAt: String?,
    val locationVerificationStatus: String? = null,
)

data class MissionSubmitResult(
    val storyId: Long,
    val chapterId: Long,
    val isCompleted: Boolean,
    val completedAt: String?,
    val nextChapterId: Long?,
)

data class MissionFileSubmitResult(
    val storyId: Long,
    val chapterId: Long,
    val fileUrl: String,
    val type: String,
    val uploadedAt: String?,
    val locationVerificationStatus: String? = null,
)

data class StoryQuestSnapshot(
    val story: StoryDetail,
    val progress: StoryProgress,
    val currentChapter: StoryChapterSummary?,
)

data class StoryChapterSnapshot(
    val story: StoryDetail,
    val progress: StoryProgress,
    val chapter: StoryChapterDetail,
)

enum class StoryStatus {
    InProgress,
    Completed,
    Unknown,
}

enum class StoryMissionType {
    Photo,
    Choice,
    Quiz,
    Action,
    Text,
    Unknown,
}
