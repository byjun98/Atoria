package com.ssafy.culture.data.course

import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.domain.model.CourseDetail
import com.ssafy.culture.domain.model.CoursePageInfo
import com.ssafy.culture.domain.model.CoursePlace
import com.ssafy.culture.domain.model.CoursePlaceSummary
import com.ssafy.culture.domain.model.CourseSelectionDraft
import com.ssafy.culture.domain.model.CourseSummary
import com.ssafy.culture.domain.model.PlaceDetail
import com.ssafy.culture.domain.model.PlaceListResult
import com.ssafy.culture.domain.model.PlaceSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

interface CourseRepository {
    suspend fun getCourses(): List<CourseSummary>
    suspend fun getCourseDetail(courseId: Long): CourseDetail
    fun saveCourseSelectionDraft(draft: CourseSelectionDraft)
    fun getCourseSelectionDraft(): CourseSelectionDraft?
    suspend fun getPlaces(
        category: String? = null,
        keyword: String? = null,
        page: Int? = null,
        size: Int? = null,
    ): PlaceListResult
    suspend fun getPlaceDetail(placeId: Long): PlaceDetail
}

@Singleton
class DefaultCourseRepository @Inject constructor(
    retrofit: Retrofit,
) : CourseRepository {
    private val courseApi: CourseApi = retrofit.create(CourseApi::class.java)
    private var courseSelectionDraft: CourseSelectionDraft? = null

    override suspend fun getCourses(): List<CourseSummary> = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockCourses
        } else {
            courseApi.getCourses().requireData().map(CourseListItemDto::toDomain)
        }
    }

    override suspend fun getCourseDetail(courseId: Long): CourseDetail = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockCourseDetails.getValue(courseId)
        } else {
            courseApi.getCourseDetail(courseId).requireData().toDomain()
        }
    }

    override fun saveCourseSelectionDraft(draft: CourseSelectionDraft) {
        courseSelectionDraft = draft
    }

    override fun getCourseSelectionDraft(): CourseSelectionDraft? = courseSelectionDraft

    override suspend fun getPlaces(
        category: String?,
        keyword: String?,
        page: Int?,
        size: Int?,
    ): PlaceListResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            val filteredPlaces = MockPlaces.filter { place ->
                val matchesCategory = category.isNullOrBlank() || place.category == category
                val matchesKeyword = keyword.isNullOrBlank() || place.title.contains(keyword)
                matchesCategory && matchesKeyword
            }
            PlaceListResult(
                places = filteredPlaces.map(PlaceDetail::toSummary),
                pageInfo = CoursePageInfo(
                    page = page ?: 0,
                    size = size ?: filteredPlaces.size,
                    totalElements = filteredPlaces.size,
                    totalPages = 1,
                ),
            )
        } else {
            val response = courseApi.getPlaces(
                category = category,
                keyword = keyword,
                page = page,
                size = size,
            )
            PlaceListResult(
                places = response.requireData().map(PlaceListItemDto::toDomain),
                pageInfo = response.pageInfo?.toDomain(),
            )
        }
    }

    override suspend fun getPlaceDetail(placeId: Long): PlaceDetail = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            MockPlaces.first { place -> place.id == placeId }
        } else {
            courseApi.getPlaceDetail(placeId).requireData().toDomain()
        }
    }
}

private fun <T> CourseApiResponse<T>.requireData(): T {
    if (success == false) {
        throw IllegalStateException(message ?: error ?: "Course API request failed.")
    }
    return data ?: throw IllegalStateException(message ?: "Course API response data is empty.")
}

private fun CourseListItemDto.toDomain(): CourseSummary =
    CourseSummary(
        id = courseId ?: 0L,
        title = title.orEmpty(),
        description = description.orEmpty(),
        places = places.orEmpty().map(CoursePlaceSummaryDto::toDomain),
    )

private fun CourseDetailDto.toDomain(): CourseDetail =
    CourseDetail(
        id = courseId ?: 0L,
        title = title.orEmpty(),
        description = description.orEmpty(),
        places = places.orEmpty().map(CoursePlaceDto::toDomain),
    )

private fun CoursePlaceSummaryDto.toDomain(): CoursePlaceSummary =
    CoursePlaceSummary(
        id = placeId ?: 0L,
        title = title.orEmpty(),
    )

private fun CoursePlaceDto.toDomain(): CoursePlace =
    CoursePlace(
        id = placeId ?: 0L,
        title = title.orEmpty(),
        latitude = latitude,
        longitude = longitude,
    )

private fun PlaceListItemDto.toDomain(): PlaceSummary =
    PlaceSummary(
        id = placeId ?: 0L,
        title = title.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        thumbnailUrl = thumbnailUrl,
    )

private fun PlaceDetailDto.toDomain(): PlaceDetail =
    PlaceDetail(
        id = placeId ?: 0L,
        title = title.orEmpty(),
        latitude = latitude,
        longitude = longitude,
        description = description.orEmpty(),
        address = address.orEmpty(),
        category = category.orEmpty(),
        thumbnailUrl = thumbnailUrl,
    )

private fun CoursePageInfoDto.toDomain(): CoursePageInfo =
    CoursePageInfo(
        page = page ?: 0,
        size = size ?: 0,
        totalElements = totalElements ?: 0,
        totalPages = totalPages ?: 0,
    )

private fun PlaceDetail.toSummary(): PlaceSummary =
    PlaceSummary(
        id = id,
        title = title,
        latitude = latitude,
        longitude = longitude,
        thumbnailUrl = thumbnailUrl,
    )

private val MockCourses: List<CourseSummary> = listOf(
    CourseSummary(
        id = 1L,
        title = "경주 역사 탐방 코스",
        description = "첨성대, 불국사, 석굴암을 따라가는 코스",
        places = listOf(
            CoursePlaceSummary(id = 101L, title = "첨성대"),
            CoursePlaceSummary(id = 102L, title = "불국사"),
            CoursePlaceSummary(id = 103L, title = "석굴암"),
        ),
    ),
    CourseSummary(
        id = 2L,
        title = "왕의 길 산책 코스",
        description = "대릉원과 월정교를 지나 신라의 밤을 만나는 코스",
        places = listOf(
            CoursePlaceSummary(id = 104L, title = "대릉원"),
            CoursePlaceSummary(id = 105L, title = "월정교"),
            CoursePlaceSummary(id = 106L, title = "동궁과 월지"),
        ),
    ),
    CourseSummary(
        id = 3L,
        title = "가족 문화 나들이",
        description = "박물관과 숲길을 함께 즐기는 쉬운 가족 코스",
        places = listOf(
            CoursePlaceSummary(id = 107L, title = "국립경주박물관"),
            CoursePlaceSummary(id = 108L, title = "교촌마을"),
            CoursePlaceSummary(id = 109L, title = "황리단길"),
        ),
    ),
)

private val MockCourseDetails: Map<Long, CourseDetail> = mapOf(
    1L to CourseDetail(
        id = 1L,
        title = "경주 역사 탐방 코스",
        description = "첨성대, 불국사, 석굴암을 따라가는 코스",
        places = listOf(
            CoursePlace(id = 101L, title = "첨성대", latitude = 35.8347, longitude = 128.9769),
            CoursePlace(id = 102L, title = "불국사", latitude = 35.7900, longitude = 129.3300),
            CoursePlace(id = 103L, title = "석굴암", latitude = 35.8240, longitude = 129.3450),
        ),
    ),
    2L to CourseDetail(
        id = 2L,
        title = "왕의 길 산책 코스",
        description = "대릉원과 월정교를 지나 신라의 밤을 만나는 코스",
        places = listOf(
            CoursePlace(id = 104L, title = "대릉원", latitude = 35.8379, longitude = 129.2104),
            CoursePlace(id = 105L, title = "월정교", latitude = 35.8295, longitude = 129.2187),
            CoursePlace(id = 106L, title = "동궁과 월지", latitude = 35.8346, longitude = 129.2266),
        ),
    ),
    3L to CourseDetail(
        id = 3L,
        title = "가족 문화 나들이",
        description = "박물관과 숲길을 함께 즐기는 쉬운 가족 코스",
        places = listOf(
            CoursePlace(id = 107L, title = "국립경주박물관", latitude = 35.8293, longitude = 129.2271),
            CoursePlace(id = 108L, title = "교촌마을", latitude = 35.8299, longitude = 129.2142),
            CoursePlace(id = 109L, title = "황리단길", latitude = 35.8375, longitude = 129.2097),
        ),
    ),
)

private val MockPlaces: List<PlaceDetail> = listOf(
    PlaceDetail(
        id = 101L,
        title = "첨성대",
        latitude = 35.8347,
        longitude = 128.9769,
        description = "신라 시대의 천문 관측대",
        address = "경상북도 경주시 인왕동 839-1",
        category = "heritage",
        thumbnailUrl = "https://cdn-url/thumb-cheomseongdae.png",
    ),
    PlaceDetail(
        id = 102L,
        title = "불국사",
        latitude = 35.7900,
        longitude = 129.3300,
        description = "통일신라 불교 문화의 대표 사찰",
        address = "경상북도 경주시 불국로 385",
        category = "heritage",
        thumbnailUrl = "https://cdn-url/thumb-bulguksa.png",
    ),
    PlaceDetail(
        id = 103L,
        title = "석굴암",
        latitude = 35.8240,
        longitude = 129.3450,
        description = "토함산에 자리한 신라 석굴 사원",
        address = "경상북도 경주시 석굴로 238",
        category = "heritage",
        thumbnailUrl = "https://cdn-url/thumb-seokguram.png",
    ),
    PlaceDetail(
        id = 104L,
        title = "대릉원",
        latitude = 35.8379,
        longitude = 129.2104,
        description = "신라 왕과 귀족의 고분이 모인 산책지",
        address = "경상북도 경주시 황남동 31-1",
        category = "heritage",
        thumbnailUrl = "https://cdn-url/thumb-daereungwon.png",
    ),
    PlaceDetail(
        id = 105L,
        title = "월정교",
        latitude = 35.8295,
        longitude = 129.2187,
        description = "남천 위에 복원된 신라 시대 교량",
        address = "경상북도 경주시 교동 274",
        category = "photo",
        thumbnailUrl = "https://cdn-url/thumb-woljeonggyo.png",
    ),
    PlaceDetail(
        id = 106L,
        title = "동궁과 월지",
        latitude = 35.8346,
        longitude = 129.2266,
        description = "야경이 아름다운 신라 왕궁 별궁 터",
        address = "경상북도 경주시 원화로 102",
        category = "photo",
        thumbnailUrl = "https://cdn-url/thumb-donggung.png",
    ),
    PlaceDetail(
        id = 107L,
        title = "국립경주박물관",
        latitude = 35.8293,
        longitude = 129.2271,
        description = "신라 문화유산을 깊이 만나는 대표 박물관",
        address = "경상북도 경주시 일정로 186",
        category = "museum",
        thumbnailUrl = "https://cdn-url/thumb-gyeongju-museum.png",
    ),
    PlaceDetail(
        id = 108L,
        title = "교촌마을",
        latitude = 35.8299,
        longitude = 129.2142,
        description = "한옥과 전통 체험을 함께 즐길 수 있는 마을",
        address = "경상북도 경주시 교촌길 39-2",
        category = "heritage",
        thumbnailUrl = "https://cdn-url/thumb-gyochon.png",
    ),
    PlaceDetail(
        id = 109L,
        title = "황리단길",
        latitude = 35.8375,
        longitude = 129.2097,
        description = "카페와 상점이 이어지는 경주의 산책 명소",
        address = "경상북도 경주시 포석로 일대",
        category = "walk",
        thumbnailUrl = "https://cdn-url/thumb-hwangridan.png",
    ),
)
