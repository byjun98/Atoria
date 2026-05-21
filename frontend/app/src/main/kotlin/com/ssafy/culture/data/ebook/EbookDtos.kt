package com.ssafy.culture.data.ebook

import com.google.gson.annotations.SerializedName

data class EbookGenerationRequestDto(
    val courseId: Long,
    val storyId: Long,
    val title: String,
    val options: Map<String, Any?> = emptyMap(),
)

data class EbookGenerationResponseDto(
    val ebookId: String?,
    val status: String?,
)

data class EbookDto(
    val ebookId: String?,
    val fileId: String?,
    val type: String?,
    val title: String?,
    val fileKey: String?,
    val fileUrl: String?,
    val thumbnailKey: String?,
    val thumbnailUrl: String?,
    val status: String?,
    val createdAt: String?,
    val metadata: EbookMetadataDto?,
)

data class EbookMetadataDto(
    val pageCount: Int?,
    val duration: String?,
    val meta: EbookContentMetaDto?,
    val cover: EbookCoverDto?,
    val pages: List<EbookContentPageDto>?,
    @SerializedName("route_points")
    val routePoints: List<EbookRoutePointDto>? = null,
)

data class EbookRoutePointDto(
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long? = null,
)

data class EbookContentMetaDto(
    val title: String?,
    val subtitle: String?,
    val author: String?,
    @SerializedName("page_count")
    val pageCount: Int?,
    val language: String?,
)

data class EbookCoverDto(
    val title: String?,
    @SerializedName("background_color")
    val backgroundColor: String?,
    @SerializedName("thumbnail_hint")
    val thumbnailHint: String?,
)

data class EbookContentPageDto(
    @SerializedName("page_number")
    val pageNumber: Int?,
    val type: String?,
    val layout: String?,
    val sequence: Int?,
    val title: String?,
    val subtitle: String?,
    val text: String?,
    @SerializedName("image_url")
    val imageUrl: String?,
    val caption: String?,
    val quote: String?,
)

data class PresignedUrlRequestDto(
    val fileName: String,
    val contentType: String,
)

data class PresignedUrlDto(
    val presignedUrl: String?,
    val fileKey: String?,
    val publicUrl: String?,
)
