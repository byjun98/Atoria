package com.ssafy.culture.data.ebook

import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.data.remote.ApiResponse
import com.ssafy.culture.domain.model.EbookGenerationResult
import com.ssafy.culture.domain.model.EbookContent
import com.ssafy.culture.domain.model.EbookContentMeta
import com.ssafy.culture.domain.model.EbookContentPage
import com.ssafy.culture.domain.model.EbookCover
import com.ssafy.culture.domain.model.EbookMetadata
import com.ssafy.culture.domain.model.EbookResult
import com.ssafy.culture.domain.model.EbookStatus
import com.ssafy.culture.domain.model.EbookType
import com.ssafy.culture.domain.model.PresignedUploadUrl
import com.ssafy.culture.domain.model.RoutePoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit

@Singleton
class EbookRepository @Inject constructor(
    retrofit: Retrofit,
) {
    private val ebookApi: EbookApi = retrofit.create(EbookApi::class.java)

    suspend fun requestEbookGeneration(
        courseId: Long,
        storyId: Long,
        title: String,
        options: Map<String, Any?> = emptyMap(),
        accessToken: String? = null,
    ): EbookGenerationResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext EbookGenerationResult(
                ebookId = MockLatestResult.fileId,
                status = MockLatestResult.status,
            )
        }
        ebookApi.requestEbookGeneration(
            authorization = accessToken.toAuthorizationHeader(),
            request = EbookGenerationRequestDto(
                courseId = courseId,
                storyId = storyId,
                title = title,
                options = options,
            ),
        ).requireData().toDomain()
    }

    suspend fun getEbooks(
        accessToken: String? = null,
    ): List<EbookResult> = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext MockEbookResults
        }
        ebookApi.getEbooks(
            authorization = accessToken.toAuthorizationHeader(),
        ).requireData().map(EbookDto::toDomain)
    }

    suspend fun getEbookDetail(
        ebookId: String,
        accessToken: String? = null,
    ): EbookResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext MockEbookResults.firstOrNull { ebook -> ebook.fileId == ebookId }
                ?: MockLatestResult
        }
        ebookApi.getEbookDetail(
            authorization = accessToken.toAuthorizationHeader(),
            ebookId = ebookId,
        ).requireData().toDomain()
    }

    suspend fun getLatestResult(
        accessToken: String? = null,
    ): EbookResult = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext MockLatestResult
        }
        val latestResult = ebookApi.getEbooks(
            authorization = accessToken.toAuthorizationHeader(),
        ).requireData()
            .map(EbookDto::toDomain)
            .maxWithOrNull(compareBy<EbookResult> { result ->
                result.status == EbookStatus.Completed
            }.thenBy { result ->
                result.createdAt
            })
            ?: error("No ebook results found.")
        runCatching {
            ebookApi.getEbookDetail(
                authorization = accessToken.toAuthorizationHeader(),
                ebookId = latestResult.fileId,
            ).requireData().toDomain()
        }.getOrElse {
            latestResult
        }
    }

    suspend fun getPresignedUrl(
        request: PresignedUrlRequestDto,
        accessToken: String? = null,
    ): PresignedUploadUrl = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext PresignedUploadUrl(
                presignedUrl = "https://s3.example.com/mock-photo.png?X-Amz-Signature=mock",
                fileKey = "uploads/mock-photo.png",
            )
        }
        ebookApi.getPresignedUrl(
            authorization = accessToken.toAuthorizationHeader(),
            request = request,
        ).toDomain()
    }
}

private fun String?.toAuthorizationHeader(): String? =
    this?.takeIf(String::isNotBlank)?.let { token -> "Bearer $token" }

private fun <T> ApiResponse<T>.requireData(): T {
    if (!success) {
        error(message.ifBlank { "Ebook API request failed." })
    }
    return data ?: error(message.ifBlank { "Ebook API returned empty data." })
}

private fun EbookGenerationResponseDto.toDomain(): EbookGenerationResult =
    EbookGenerationResult(
        ebookId = ebookId.orEmpty(),
        status = EbookStatus.fromApiValue(status),
    )

private fun EbookDto.toDomain(): EbookResult =
    EbookResult(
        fileId = ebookId ?: fileId.orEmpty(),
        type = EbookType.fromApiValue(type ?: EbookType.Ebook.apiValue),
        title = title.orEmpty().withoutExtraSubjectParticle(),
        fileUrl = fileUrl ?: fileKey.orEmpty(),
        thumbnailUrl = thumbnailUrl ?: thumbnailKey.orEmpty(),
        status = EbookStatus.fromApiValue(status),
        createdAt = createdAt.orEmpty(),
        metadata = metadata?.toDomain(),
    )

private fun EbookMetadataDto.toDomain(): EbookMetadata =
    EbookMetadata(
        pageCount = pageCount ?: meta?.pageCount ?: pages?.size,
        duration = duration,
        content = toContentDomain(),
        routePoints = routePoints
            ?.mapNotNull { dto ->
                val lat: Double = dto.latitude ?: return@mapNotNull null
                val lng: Double = dto.longitude ?: return@mapNotNull null
                RoutePoint(
                    latitude = lat,
                    longitude = lng,
                    timestamp = dto.timestamp ?: 0L,
                )
            }
            .orEmpty(),
    )

private fun EbookMetadataDto.toContentDomain(): EbookContent? {
    val contentPages: List<EbookContentPage> = pages?.map(EbookContentPageDto::toDomain).orEmpty()
    if (meta == null && cover == null && contentPages.isEmpty()) return null
    return EbookContent(
        meta = meta?.toDomain(),
        cover = cover?.toDomain(),
        pages = contentPages,
    )
}

private fun EbookContentMetaDto.toDomain(): EbookContentMeta =
    EbookContentMeta(
        title = title?.withoutExtraSubjectParticle(),
        subtitle = subtitle,
        author = author,
        pageCount = pageCount,
        language = language,
    )

private fun EbookCoverDto.toDomain(): EbookCover =
    EbookCover(
        title = title?.withoutExtraSubjectParticle(),
        backgroundColor = backgroundColor,
        thumbnailHint = thumbnailHint,
    )

private fun EbookContentPageDto.toDomain(): EbookContentPage =
    EbookContentPage(
        pageNumber = pageNumber,
        type = type,
        layout = layout,
        sequence = sequence,
        title = title?.withoutExtraSubjectParticle(),
        subtitle = subtitle,
        text = text,
        imageUrl = imageUrl,
        caption = caption,
        quote = quote,
    )

private fun PresignedUrlDto.toDomain(): PresignedUploadUrl =
    PresignedUploadUrl(
        presignedUrl = presignedUrl.orEmpty(),
        fileKey = publicUrl.orEmpty().ifBlank {
            presignedUrl.orEmpty()
                .substringBefore("?")
                .takeIf { url -> url.isHttpUrl() }
                .orEmpty()
        }.ifBlank {
            fileKey.orEmpty()
        },
    )

private fun String.withoutExtraSubjectParticle(): String =
    replace("이의", "의")

private fun String.isHttpUrl(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

private val MockEbookResults: List<EbookResult> = listOf(
    EbookResult(
        fileId = "mock-processing-101",
        type = EbookType.Ebook,
        title = "덕수궁 빛의 산책",
        fileUrl = "https://cdn.example.com/ebooks/deoksugung.pdf",
        thumbnailUrl = "https://cdn.example.com/thumbs/deoksugung.png",
        status = EbookStatus.Processing,
        createdAt = "2026-04-28T16:20:00",
        metadata = EbookMetadata(
            pageCount = null,
            duration = null,
        ),
    ),
    EbookResult(
        fileId = "mock-completed-102",
        type = EbookType.Ebook,
        title = "북촌 골목의 하루",
        fileUrl = "https://cdn.example.com/ebooks/bukchon.pdf",
        thumbnailUrl = "https://cdn.example.com/thumbs/bukchon.png",
        status = EbookStatus.Completed,
        createdAt = "2026-04-26T13:10:00",
        metadata = EbookMetadata(
            pageCount = 18,
            duration = null,
        ),
    ),
    EbookResult(
        fileId = "mock-completed-103",
        type = EbookType.Ebook,
        title = "한강 위 노을 기록",
        fileUrl = "https://cdn.example.com/ebooks/hangang.pdf",
        thumbnailUrl = "https://cdn.example.com/thumbs/hangang.png",
        status = EbookStatus.Completed,
        createdAt = "2026-04-23T18:40:00",
        metadata = EbookMetadata(
            pageCount = 20,
            duration = null,
        ),
    ),
)

private val MockLatestResult: EbookResult = EbookResult(
    fileId = "mock-result-latest",
    type = EbookType.Ebook,
    title = "경주에서 만난 나의 이야기",
    fileUrl = "https://cdn.example.com/ebooks/gyeongju.pdf",
    thumbnailUrl = "https://cdn.example.com/thumbs/gyeongju.png",
    status = EbookStatus.Completed,
    createdAt = "2026-04-28T17:30:00",
    metadata = EbookMetadata(
        pageCount = 20,
        duration = null,
    ),
)
