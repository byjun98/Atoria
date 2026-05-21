package com.ssafy.culture.data.story

import android.content.Context
import androidx.core.content.edit
import com.ssafy.culture.data.course.DefaultCourseRepository
import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.domain.model.MissionFileSubmitResult
import com.ssafy.culture.domain.model.MissionSubmitResult
import com.ssafy.culture.domain.model.StoryChapterDetail
import com.ssafy.culture.domain.model.StoryChapterSnapshot
import com.ssafy.culture.domain.model.StoryChapterSummary
import com.ssafy.culture.domain.model.StoryContent
import com.ssafy.culture.domain.model.StoryCreateRequest
import com.ssafy.culture.domain.model.StoryDetail
import com.ssafy.culture.domain.model.StoryListResult
import com.ssafy.culture.domain.model.StoryMission
import com.ssafy.culture.domain.model.StoryMissionProgress
import com.ssafy.culture.domain.model.StoryMissionType
import com.ssafy.culture.domain.model.StoryPageInfo
import com.ssafy.culture.domain.model.StoryPlace
import com.ssafy.culture.domain.model.StoryProgress
import com.ssafy.culture.domain.model.StoryProgressChapter
import com.ssafy.culture.domain.model.StoryProtagonist
import com.ssafy.culture.domain.model.StoryQuestSnapshot
import com.ssafy.culture.domain.model.StoryStatus
import com.ssafy.culture.domain.model.StorySummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

@Singleton
class StoryRepository @Inject constructor(
    @ApplicationContext context: Context,
    retrofit: Retrofit,
    private val courseRepository: DefaultCourseRepository,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val storyApi: StoryApi = retrofit.create(StoryApi::class.java)
    private var protagonistDraft: List<StoryProtagonist> = emptyList()
    private var cachedCurrentStory: StoryDetail? = null

    @Volatile
    private var optimisticallyCompletedChapterIds: Set<Long> = emptySet()

    @Synchronized
    fun markChapterCompletedOptimistically(chapterId: Long) {
        optimisticallyCompletedChapterIds = optimisticallyCompletedChapterIds + chapterId
        updateCachedChapterCompletion(chapterId = chapterId)
    }

    fun remainingIncompleteChapterCountAfter(
        storyId: Long,
        chapterId: Long,
    ): Int {
        val story: StoryDetail = cachedCurrentStory
            ?.takeIf { cachedStory -> cachedStory.storyId == storyId }
            ?: return -1
        val optimistic: Set<Long> = optimisticallyCompletedChapterIds
        return story.chapters.count { chapter ->
            chapter.chapterId != chapterId &&
                !chapter.isCompleted &&
                chapter.chapterId !in optimistic
        }
    }

    private fun getAbandonedStoryIds(): Set<Long> =
        preferences.getStringSet(KeyAbandonedStoryIds, emptySet())
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            .orEmpty()

    fun abandonStory(storyId: Long) {
        val updated: Set<String> = (getAbandonedStoryIds() + storyId).map(Long::toString).toSet()
        preferences.edit {
            putStringSet(KeyAbandonedStoryIds, updated)
        }
        if (cachedCurrentStory?.storyId == storyId) {
            cachedCurrentStory = null
        }
    }

    fun setChapterLocationVerified(
        storyId: Long,
        chapterId: Long,
        isVerified: Boolean,
    ) {
        val current: Set<Long> = getUnverifiedChapterIds(storyId)
        val updated: Set<String> = if (isVerified) {
            current - chapterId
        } else {
            current + chapterId
        }.map(Long::toString).toSet()
        preferences.edit {
            putStringSet(unverifiedChapterKey(storyId), updated)
        }
    }

    suspend fun hasUnverifiedMissionSubmissions(storyId: Long): Boolean = withContext(Dispatchers.IO) {
        if (getUnverifiedChapterIds(storyId).isNotEmpty()) return@withContext true
        runCatching {
            getStoryProgress(storyId).chapters.any { chapter ->
                chapter.locationVerificationStatus == LocationVerificationUnverified
            }
        }.getOrDefault(false)
    }

    private fun clearAbandonedStory(storyId: Long) {
        val current: Set<Long> = getAbandonedStoryIds()
        if (storyId !in current) return
        val updated: Set<String> = current.minus(storyId).map(Long::toString).toSet()
        preferences.edit {
            putStringSet(KeyAbandonedStoryIds, updated)
        }
    }

    fun saveProtagonistDraft(protagonists: List<StoryProtagonist>) {
        protagonistDraft = protagonists
    }

    fun getProtagonistDraft(): List<StoryProtagonist> = protagonistDraft

    suspend fun createStory(
        request: StoryCreateRequest,
    ): StoryDetail = withContext(Dispatchers.IO) {
        val story = if (MockApiConfig.enabled) {
            MockStoryData.createStoryDetail(
                courseId = request.courseId,
                protagonists = request.protagonists,
                orderedPlaceIds = request.placeIds,
            )
        } else {
            storyApi.createStory(request.toDto()).requireData().toDomain()
        }
        cachedCurrentStory = story
        clearAbandonedStory(story.storyId)
        optimisticallyCompletedChapterIds = emptySet()
        story
    }

    suspend fun getMyStories(
        page: Int = DefaultPage,
        size: Int = DefaultPageSize,
    ): StoryListResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockStoryData.storyList()
        } else {
            storyApi.getMyStories(
                page = page,
                size = size,
            ).toDomain()
        }
    }

    suspend fun getStoryDetail(
        storyId: Long,
    ): StoryDetail = withContext(Dispatchers.IO) {
        val story = if (MockApiConfig.enabled) {
            MockStoryData.storyDetail.copy(storyId = storyId)
        } else {
            storyApi.getStoryDetail(storyId).requireData().toDomain()
        }
        if (story.isActiveStory()) {
            cachedCurrentStory = story
        }
        story
    }

    suspend fun getChapterDetail(
        storyId: Long,
        chapterId: Long,
    ): StoryChapterDetail = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockStoryData.chapterDetail(
                storyId = storyId,
                chapterId = chapterId,
            )
        } else {
            storyApi.getChapterDetail(
                storyId = storyId,
                chapterId = chapterId,
            ).requireData().toDomain()
        }
    }

    suspend fun getStoryProgress(
        storyId: Long,
    ): StoryProgress = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockStoryData.storyProgress.copy(storyId = storyId)
        } else {
            storyApi.getStoryProgress(storyId).requireData().toDomain()
        }
    }

    suspend fun submitMission(
        storyId: Long,
        chapterId: Long,
        isCompleted: Boolean = true,
    ): MissionSubmitResult = withContext(Dispatchers.IO) {
        val result = if (MockApiConfig.enabled) {
            MockStoryData.submitMission(
                storyId = storyId,
                chapterId = chapterId,
                isCompleted = isCompleted,
            )
        } else {
            storyApi.submitMission(
                storyId = storyId,
                chapterId = chapterId,
                request = MissionSubmitRequestDto(
                    result = MissionResultDto(isCompleted = isCompleted),
                ),
            ).requireData().toDomain()
        }
        if (result.isCompleted) {
            updateCachedChapterCompletion(chapterId = chapterId)
        }
        result
    }

    suspend fun submitMissionFile(
        storyId: Long,
        chapterId: Long,
        fileUrl: String,
        type: String = DefaultFileType,
        locationVerificationStatus: String? = null,
    ): MissionFileSubmitResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockStoryData.submitMissionFile(
                storyId = storyId,
                chapterId = chapterId,
                fileUrl = fileUrl,
                type = type,
                locationVerificationStatus = locationVerificationStatus,
            )
        } else {
            storyApi.submitMissionFile(
                storyId = storyId,
                chapterId = chapterId,
                request = MissionFileSubmitRequestDto(
                    fileUrl = fileUrl,
                    type = type,
                    locationVerificationStatus = locationVerificationStatus,
                ),
            ).requireData().toDomain()
        }
    }

    suspend fun getCurrentQuestSnapshot(): StoryQuestSnapshot? {
        val abandonedIds: Set<Long> = getAbandonedStoryIds()
        val storySummary: StorySummary? = runCatching {
            val storyList: StoryListResult = getMyStories()
            val availableStories: List<StorySummary> = storyList.stories
                .filter { story -> story.storyId !in abandonedIds }
            availableStories.firstOrNull { story -> story.isActiveStory() }
                ?: availableStories.firstOrNull()
        }.getOrNull()
        val cachedStory: StoryDetail? = cachedCurrentStory?.takeIf { story ->
            story.storyId !in abandonedIds
        }
        val story: StoryDetail = runCatching {
            storySummary?.let { summary -> getStoryDetail(summary.storyId) }
        }.getOrNull()
            ?: cachedStory
            ?: return null
        if (story.storyId in abandonedIds) return null
        val progress: StoryProgress = runCatching {
            getStoryProgress(story.storyId)
        }.getOrElse {
            story.toProgress()
        }.normalizedFor(story, optimisticallyCompletedChapterIds)
        val currentChapter: StoryChapterSummary? = progress.firstIncompleteChapter(story)
        if (story.isActiveStory()) {
            cachedCurrentStory = story
        }
        return StoryQuestSnapshot(
            story = story,
            progress = progress,
            currentChapter = currentChapter,
        )
    }

    suspend fun getCurrentChapterSnapshot(): StoryChapterSnapshot? {
        val questSnapshot: StoryQuestSnapshot = getCurrentQuestSnapshot() ?: return null
        val currentChapter: StoryChapterSummary = questSnapshot.currentChapter ?: return null
        val chapter: StoryChapterDetail = getChapterDetail(
            storyId = questSnapshot.story.storyId,
            chapterId = currentChapter.chapterId,
        )
        return StoryChapterSnapshot(
            story = questSnapshot.story,
            progress = questSnapshot.progress,
            chapter = chapter,
        )
    }

    suspend fun getChapterSnapshot(
        storyId: Long,
        chapterId: Long,
    ): StoryChapterSnapshot = withContext(Dispatchers.IO) {
        val story: StoryDetail = cachedCurrentStory
            ?.takeIf { cachedStory -> cachedStory.storyId == storyId }
            ?: getStoryDetail(storyId)
        val progress: StoryProgress = runCatching {
            getStoryProgress(storyId)
        }.getOrElse {
            story.toProgress()
        }.normalizedFor(story, optimisticallyCompletedChapterIds)
        val chapter: StoryChapterDetail = getChapterDetail(
            storyId = storyId,
            chapterId = chapterId,
        )
        StoryChapterSnapshot(
            story = story,
            progress = progress,
            chapter = chapter,
        )
    }

    private fun getUnverifiedChapterIds(storyId: Long): Set<Long> =
        preferences.getStringSet(unverifiedChapterKey(storyId), emptySet())
            ?.mapNotNull(String::toLongOrNull)
            ?.toSet()
            .orEmpty()

    private fun unverifiedChapterKey(storyId: Long): String =
        "${KeyUnverifiedChapterIdsPrefix}_$storyId"

    private companion object {
        const val DefaultPage = 0
        const val DefaultPageSize = 10
        const val DefaultFileType = "IMAGE"
        const val LocationVerificationUnverified = "UNVERIFIED"
        const val PreferencesName = "story_local_state"
        const val KeyAbandonedStoryIds = "abandoned_story_ids"
        const val KeyUnverifiedChapterIdsPrefix = "unverified_chapter_ids"
    }

    private fun updateCachedChapterCompletion(chapterId: Long) {
        val story: StoryDetail = cachedCurrentStory ?: return
        cachedCurrentStory = story.copy(
            chapters = story.chapters.map { chapter ->
                if (chapter.chapterId == chapterId) {
                    chapter.copy(isCompleted = true)
                } else {
                    chapter
                }
            },
        )
    }
}

private fun StoryCreateRequest.toDto(): StoryCreateRequestDto =
    StoryCreateRequestDto(
        courseId = courseId,
        protagonists = protagonists.map(StoryProtagonist::toDto),
        placeIds = placeIds,
    )

private fun StoryProtagonist.toDto(): StoryProtagonistDto =
    StoryProtagonistDto(
        name = name,
        age = age,
        tendency = tendency,
    )

private fun StoryListResponseDto.toDomain(): StoryListResult =
    StoryListResult(
        stories = data.orEmpty().map(StorySummaryDto::toDomain),
        pageInfo = pageInfo?.toDomain(),
    )

private fun StoryPageInfoDto.toDomain(): StoryPageInfo =
    StoryPageInfo(
        page = page ?: 0,
        size = size ?: 0,
        totalElements = totalElements ?: 0,
        totalPages = totalPages ?: 0,
    )

private fun StorySummaryDto.toDomain(): StorySummary =
    StorySummary(
        storyId = storyId ?: 0L,
        courseId = courseId ?: 0L,
        title = title.orEmpty().withoutExtraSubjectParticle(),
        status = status.toStoryStatus(),
        thumbnailUrl = thumbnailUrl,
        completedCount = completedCount ?: 0,
        totalCount = totalCount ?: 0,
        createdAt = createdAt.orEmpty(),
    )

private fun StoryDetailDto.toDomain(): StoryDetail =
    StoryDetail(
        storyId = storyId ?: 0L,
        courseId = courseId ?: 0L,
        title = title.orEmpty().withoutExtraSubjectParticle(),
        status = status.toStoryStatus(),
        intro = intro,
        outro = outro,
        protagonists = protagonists.orEmpty().map(StoryProtagonistDto::toDomain),
        chapters = chapters.orEmpty().map(StoryChapterSummaryDto::toDomain),
        createdAt = createdAt.orEmpty(),
    )

private fun StoryProtagonistDto.toDomain(): StoryProtagonist =
    StoryProtagonist(
        name = name.orEmpty(),
        age = age ?: 0,
        tendency = tendency.orEmpty(),
    )

private fun StoryChapterSummaryDto.toDomain(): StoryChapterSummary =
    StoryChapterSummary(
        chapterId = chapterId ?: 0L,
        sequence = sequence ?: 0,
        placeId = placeId,
        placeTitle = placeTitle.orEmpty(),
        isCompleted = isCompleted ?: false,
    )

private fun StoryProgressDto.toDomain(): StoryProgress =
    StoryProgress(
        storyId = storyId ?: 0L,
        totalCount = totalCount ?: 0,
        completedCount = completedCount ?: 0,
        progressRate = progressRate ?: 0f,
        chapters = chapters.orEmpty().map(StoryProgressChapterDto::toDomain),
    )

private fun StoryProgressChapterDto.toDomain(): StoryProgressChapter =
    StoryProgressChapter(
        chapterId = chapterId ?: 0L,
        placeTitle = placeTitle.orEmpty(),
        isCompleted = isCompleted ?: false,
        locationVerificationStatus = locationVerificationStatus,
    )

private fun StoryChapterDetailDto.toDomain(): StoryChapterDetail =
    StoryChapterDetail(
        chapterId = chapterId ?: 0L,
        sequence = sequence ?: 0,
        isCompleted = isCompleted ?: false,
        place = place.toDomain(),
        storyContent = story.toDomain(),
        mission = mission.toDomain(),
    )

private fun StoryPlaceDto?.toDomain(): StoryPlace =
    StoryPlace(
        placeId = this?.placeId ?: 0L,
        title = this?.title.orEmpty(),
        latitude = this?.latitude,
        longitude = this?.longitude,
    )

private fun StoryContentDto?.toDomain(): StoryContent =
    StoryContent(
        content = this?.content,
    )

private fun StoryMissionDto?.toDomain(): StoryMission =
    StoryMission(
        title = this?.title.orEmpty(),
        description = this?.description.orEmpty(),
        verificationHint = this?.verificationHint.orEmpty(),
        type = this?.type.toMissionType(),
        progress = this?.progress.toDomain(),
    )

private fun StoryMissionProgressDto?.toDomain(): StoryMissionProgress =
    StoryMissionProgress(
        isCompleted = this?.isCompleted ?: false,
        fileUrl = this?.fileUrl,
        completedAt = this?.completedAt,
        locationVerificationStatus = this?.locationVerificationStatus,
    )

private fun MissionSubmitResultDto.toDomain(): MissionSubmitResult =
    MissionSubmitResult(
        storyId = storyId ?: 0L,
        chapterId = chapterId ?: 0L,
        isCompleted = isCompleted ?: false,
        completedAt = completedAt,
        nextChapterId = nextChapterId,
    )

private fun MissionFileSubmitResultDto.toDomain(): MissionFileSubmitResult =
    MissionFileSubmitResult(
        storyId = storyId ?: 0L,
        chapterId = chapterId ?: 0L,
        fileUrl = fileUrl.orEmpty(),
        type = type.orEmpty(),
        uploadedAt = uploadedAt,
        locationVerificationStatus = locationVerificationStatus,
    )

private fun String?.toStoryStatus(): StoryStatus =
    when (this?.uppercase()) {
        "IN_PROGRESS" -> StoryStatus.InProgress
        "COMPLETED" -> StoryStatus.Completed
        else -> StoryStatus.Unknown
    }

private fun String?.toMissionType(): StoryMissionType =
    when (this?.uppercase()) {
        "PHOTO" -> StoryMissionType.Photo
        "CHOICE" -> StoryMissionType.Choice
        "QUIZ" -> StoryMissionType.Quiz
        "ACTION" -> StoryMissionType.Action
        "TEXT" -> StoryMissionType.Text
        else -> StoryMissionType.Unknown
    }

private fun String.withoutExtraSubjectParticle(): String =
    replace("이의", "의")

private fun <T> StoryApiResponseDto<T>.requireData(): T =
    data ?: error(message ?: "Story API response data is empty.")

private fun StorySummary.isActiveStory(): Boolean =
    status == StoryStatus.InProgress || totalCount == 0 || completedCount < totalCount

private fun StoryDetail.isActiveStory(): Boolean =
    status == StoryStatus.InProgress || chapters.any { chapter -> !chapter.isCompleted }

private fun StoryProgress.normalizedFor(
    story: StoryDetail,
    optimisticallyCompleted: Set<Long> = emptySet(),
): StoryProgress {
    val chaptersById: Map<Long, StoryProgressChapter> = chapters.associateBy(StoryProgressChapter::chapterId)
    val normalizedChapters: List<StoryProgressChapter> = if (story.chapters.isNotEmpty()) {
        story.chapters.map { chapter ->
            val progressChapter: StoryProgressChapter? = chaptersById[chapter.chapterId]
            val serverCompleted: Boolean = progressChapter?.isCompleted ?: chapter.isCompleted
            StoryProgressChapter(
                chapterId = chapter.chapterId,
                placeTitle = progressChapter?.placeTitle?.takeIf(String::isNotBlank)
                    ?: chapter.placeTitle,
                isCompleted = serverCompleted || chapter.chapterId in optimisticallyCompleted,
                locationVerificationStatus = progressChapter?.locationVerificationStatus,
            )
        }
    } else {
        chapters
    }
    val normalizedTotalCount: Int = story.chapters.size.takeIf { count -> count > 0 }
        ?: totalCount.takeIf { count -> count > 0 }
        ?: normalizedChapters.size
    val normalizedCompletedCount: Int = normalizedChapters.count { it.isCompleted }
    val normalizedProgressRate: Float = if (normalizedTotalCount == 0) {
        0f
    } else {
        normalizedCompletedCount.toFloat() / normalizedTotalCount.toFloat()
    }
    return copy(
        totalCount = normalizedTotalCount,
        completedCount = normalizedCompletedCount,
        progressRate = normalizedProgressRate,
        chapters = normalizedChapters,
    )
}

private fun StoryProgress.firstIncompleteChapter(story: StoryDetail): StoryChapterSummary? {
    val storyChaptersById: Map<Long, StoryChapterSummary> = story.chapters.associateBy(StoryChapterSummary::chapterId)
    return chapters.withIndex()
        .firstOrNull { indexedChapter -> !indexedChapter.value.isCompleted }
        ?.let { indexedChapter ->
            storyChaptersById[indexedChapter.value.chapterId]
                ?.copy(isCompleted = indexedChapter.value.isCompleted)
                ?: StoryChapterSummary(
                    chapterId = indexedChapter.value.chapterId,
                    sequence = indexedChapter.index + 1,
                    placeId = null,
                    placeTitle = indexedChapter.value.placeTitle,
                    isCompleted = indexedChapter.value.isCompleted,
                )
        }
}

private fun StoryDetail.toProgress(): StoryProgress {
    val completedCount: Int = chapters.count { chapter -> chapter.isCompleted }
    val totalCount: Int = chapters.size
    return StoryProgress(
        storyId = storyId,
        totalCount = totalCount,
        completedCount = completedCount,
        progressRate = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount.toFloat(),
        chapters = chapters.map { chapter ->
            StoryProgressChapter(
                chapterId = chapter.chapterId,
                placeTitle = chapter.placeTitle,
                isCompleted = chapter.isCompleted,
            )
        },
    )
}

private object MockStoryData {
    private data class MockPlace(
        val id: Long,
        val title: String,
        val latitude: Double,
        val longitude: Double,
    )

    private val placesById: Map<Long, MockPlace> = listOf(
        MockPlace(id = 101L, title = "첨성대", latitude = 35.8347, longitude = 129.2187),
        MockPlace(id = 102L, title = "불국사", latitude = 35.7900, longitude = 129.3300),
        MockPlace(id = 110L, title = "석빙고", latitude = 35.8354, longitude = 129.2206),
        MockPlace(id = 106L, title = "동궁과 월지", latitude = 35.8346, longitude = 129.2266),
    ).associateBy(MockPlace::id)

    private val defaultPlaces: List<MockPlace> = listOf(
        placesById.getValue(101L),
        placesById.getValue(102L),
    )

    private val protagonists: List<StoryProtagonist> = listOf(
        StoryProtagonist(name = "민준", age = 5, tendency = "모험적"),
        StoryProtagonist(name = "성준", age = 6, tendency = "호기심 많음"),
    )
    private val chapters: List<StoryChapterSummary> = buildChapters(defaultPlaces)
    private var currentStoryDetail: StoryDetail = StoryDetail(
        storyId = 7001L,
        courseId = 1L,
        title = "민준의 경주 시간여행",
        status = StoryStatus.InProgress,
        intro = "민준이와 성준이는 경주에서 신비한 시간여행을 시작했다...",
        outro = null,
        protagonists = protagonists,
        chapters = chapters,
        createdAt = "2026-04-23T10:00:00",
    )
    val storyDetail: StoryDetail
        get() = currentStoryDetail
    val storyProgress: StoryProgress
        get() = buildStoryProgress(currentStoryDetail.storyId, currentStoryDetail.chapters)

    fun createStoryDetail(
        courseId: Long,
        protagonists: List<StoryProtagonist>,
        orderedPlaceIds: List<Long>,
    ): StoryDetail {
        val draftPlaces: List<MockPlace> = orderedPlaceIds
            .mapNotNull(placesById::get)
        val orderedPlaces: List<MockPlace> = draftPlaces.ifEmpty { defaultPlaces }
        val chapters: List<StoryChapterSummary> = buildChapters(orderedPlaces)
        val activeProtagonists: List<StoryProtagonist> = protagonists.ifEmpty { this.protagonists }
        val detail = storyDetail.copy(
            courseId = courseId,
            title = storyTitle(activeProtagonists),
            intro = storyIntro(activeProtagonists),
            protagonists = activeProtagonists,
            chapters = chapters,
        )
        currentStoryDetail = detail
        return detail
    }

    fun storyList(): StoryListResult =
        StoryListResult(
            stories = listOf(
                StorySummary(
                    storyId = storyDetail.storyId,
                    courseId = storyDetail.courseId,
                    title = storyDetail.title,
                    status = storyDetail.status,
                    thumbnailUrl = null,
                    completedCount = storyProgress.completedCount,
                    totalCount = storyProgress.totalCount,
                    createdAt = storyDetail.createdAt,
                ),
            ),
            pageInfo = StoryPageInfo(
                page = 0,
                size = 10,
                totalElements = 1,
                totalPages = 1,
            ),
        )

    fun chapterDetail(
        storyId: Long,
        chapterId: Long,
    ): StoryChapterDetail {
        val activeChapters: List<StoryChapterSummary> = currentStoryDetail.chapters
        val chapter: StoryChapterSummary = activeChapters.firstOrNull { item ->
            item.chapterId == chapterId
        } ?: activeChapters.first { item -> !item.isCompleted }
        val place: MockPlace = placesById[chapter.placeId] ?: defaultPlaces.first()
        return StoryChapterDetail(
            chapterId = chapter.chapterId,
            sequence = chapter.sequence,
            isCompleted = chapter.isCompleted,
            place = StoryPlace(
                placeId = place.id,
                title = place.title,
                latitude = place.latitude,
                longitude = place.longitude,
            ),
            storyContent = StoryContent(
                content = missionDescription(chapter),
            ),
            mission = StoryMission(
                title = missionTitle(chapter),
                description = missionDescription(chapter),
                verificationHint = "",
                type = StoryMissionType.Photo,
                progress = StoryMissionProgress(
                    isCompleted = false,
                    fileUrl = null,
                    completedAt = null,
                ),
            ),
        )
    }

    fun submitMission(
        storyId: Long,
        chapterId: Long,
        isCompleted: Boolean,
    ): MissionSubmitResult {
        if (isCompleted) {
            currentStoryDetail = currentStoryDetail.copy(
                chapters = currentStoryDetail.chapters.map { chapter ->
                    if (chapter.chapterId == chapterId) {
                        chapter.copy(isCompleted = true)
                    } else {
                        chapter
                    }
                },
            )
        }
        val nextChapterId: Long? = currentStoryDetail.chapters
            .firstOrNull { chapter -> !chapter.isCompleted }
            ?.chapterId
        return MissionSubmitResult(
            storyId = storyId,
            chapterId = chapterId,
            isCompleted = isCompleted,
            completedAt = "2026-04-24T11:00:00",
            nextChapterId = nextChapterId,
        )
    }

    fun submitMissionFile(
        storyId: Long,
        chapterId: Long,
        fileUrl: String,
        type: String,
        locationVerificationStatus: String?,
    ): MissionFileSubmitResult =
        MissionFileSubmitResult(
            storyId = storyId,
            chapterId = chapterId,
            fileUrl = fileUrl,
            type = type,
            uploadedAt = "2026-04-24T11:05:00",
            locationVerificationStatus = locationVerificationStatus,
        )

    private fun buildStoryProgress(
        storyId: Long,
        chapters: List<StoryChapterSummary>,
    ): StoryProgress =
        StoryProgress(
            storyId = storyId,
            totalCount = chapters.size,
            completedCount = chapters.count { chapter -> chapter.isCompleted },
            progressRate = if (chapters.isEmpty()) {
                0f
            } else {
                chapters.count { chapter -> chapter.isCompleted }.toFloat() / chapters.size
            },
            chapters = chapters.map { chapter ->
                StoryProgressChapter(
                    chapterId = chapter.chapterId,
                    placeTitle = chapter.placeTitle,
                    isCompleted = chapter.isCompleted,
                )
            },
        )

    private fun buildChapters(places: List<MockPlace>): List<StoryChapterSummary> =
        places.mapIndexed { index, place ->
            val sequence: Int = index + 1
            StoryChapterSummary(
                chapterId = 9000L + sequence,
                sequence = sequence,
                placeId = place.id,
                placeTitle = place.title,
                isCompleted = false,
            )
        }

    private fun storyTitle(protagonists: List<StoryProtagonist>): String {
        val firstName: String = protagonists.firstOrNull()?.name?.takeIf(String::isNotBlank) ?: "민준"
        return "${firstName}의 경주 시간여행"
    }

    private fun storyIntro(protagonists: List<StoryProtagonist>): String {
        val names: String = protagonists.map(StoryProtagonist::name)
            .filter(String::isNotBlank)
            .joinToString("와 ")
            .ifBlank { "민준" }
        return "${names}은 경주에서 신비한 시간여행을 시작했다..."
    }

    private fun missionTitle(chapter: StoryChapterSummary): String {
        return chapter.placeTitle
    }

    private fun missionDescription(chapter: StoryChapterSummary): String {
        return when (chapter.placeTitle) {
            "첨성대" -> "첨성대는 신라 시대의 천문 관측소입니다.\n이곳에서 선덕여왕의 별 이야기를 떠올릴 수 있습니다.\n첨성대와 함께 사진을 찍어보세요."
            "불국사" -> "불국사 앞에서 계단을 세어보세요.\n비가 오는 날에는 미끄러울 수 있으니 가까운 거리에서 세어보는 것이 좋아요.\n계단과 주변 기둥이 한 화면에 보이면 과거로 가는 열쇠를 찾은 거예요."
            else -> "${chapter.placeTitle} 전체가 보이도록 촬영"
        }
    }
}
