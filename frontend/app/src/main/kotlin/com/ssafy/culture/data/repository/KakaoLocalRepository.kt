package com.ssafy.culture.data.repository

import com.ssafy.culture.data.remote.KakaoKeywordSearchResponse
import com.ssafy.culture.data.remote.KakaoLocalApi
import com.ssafy.culture.data.remote.KakaoLocalAuthHeaderProvider
import com.ssafy.culture.data.remote.KakaoPlaceDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface KakaoLocalRepository {
    suspend fun searchKeyword(
        query: String,
        x: Double? = null,
        y: Double? = null,
        radius: Int? = null,
        page: Int = 1,
        size: Int = 15,
        sort: KakaoLocalSort = KakaoLocalSort.DISTANCE,
    ): KakaoKeywordSearchResult
}

@Singleton
class DefaultKakaoLocalRepository @Inject constructor(
    private val kakaoLocalApi: KakaoLocalApi,
    private val authHeaderProvider: KakaoLocalAuthHeaderProvider,
) : KakaoLocalRepository {
    override suspend fun searchKeyword(
        query: String,
        x: Double?,
        y: Double?,
        radius: Int?,
        page: Int,
        size: Int,
        sort: KakaoLocalSort,
    ): KakaoKeywordSearchResult = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) {
            "query must not be blank."
        }
        kakaoLocalApi.searchKeyword(
            authorization = authHeaderProvider.getAuthorizationHeader(),
            kakaoAgent = authHeaderProvider.getKakaoAgentHeader(),
            query = query,
            x = x,
            y = y,
            radius = radius,
            page = page,
            size = size,
            sort = sort.apiValue,
        ).toRepositoryModel()
    }
}

enum class KakaoLocalSort(
    val apiValue: String,
) {
    ACCURACY("accuracy"),
    DISTANCE("distance"),
}

data class KakaoKeywordSearchResult(
    val places: List<KakaoPlace>,
    val totalCount: Int,
    val pageableCount: Int,
    val isEnd: Boolean,
)

data class KakaoPlace(
    val id: String,
    val name: String,
    val categoryName: String,
    val categoryGroupCode: String,
    val categoryGroupName: String,
    val phone: String,
    val addressName: String,
    val roadAddressName: String,
    val x: Double?,
    val y: Double?,
    val placeUrl: String,
    val distanceMeters: Int?,
)

private fun KakaoKeywordSearchResponse.toRepositoryModel(): KakaoKeywordSearchResult =
    KakaoKeywordSearchResult(
        places = documents.map(KakaoPlaceDto::toRepositoryModel),
        totalCount = meta.totalCount,
        pageableCount = meta.pageableCount,
        isEnd = meta.isEnd,
    )

private fun KakaoPlaceDto.toRepositoryModel(): KakaoPlace =
    KakaoPlace(
        id = id.orEmpty(),
        name = placeName.orEmpty(),
        categoryName = categoryName.orEmpty(),
        categoryGroupCode = categoryGroupCode.orEmpty(),
        categoryGroupName = categoryGroupName.orEmpty(),
        phone = phone.orEmpty(),
        addressName = addressName.orEmpty(),
        roadAddressName = roadAddressName.orEmpty(),
        x = x?.toDoubleOrNull(),
        y = y?.toDoubleOrNull(),
        placeUrl = placeUrl.orEmpty(),
        distanceMeters = distance?.toIntOrNull(),
    )
