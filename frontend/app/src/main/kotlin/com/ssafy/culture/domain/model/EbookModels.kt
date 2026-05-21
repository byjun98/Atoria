package com.ssafy.culture.domain.model

data class EbookResult(
    val fileId: String,
    val type: EbookType,
    val title: String,
    val fileUrl: String,
    val thumbnailUrl: String,
    val status: EbookStatus,
    val createdAt: String,
    val metadata: EbookMetadata? = null,
)

data class EbookMetadata(
    val pageCount: Int?,
    val duration: String?,
    val content: EbookContent? = null,
    val routePoints: List<RoutePoint> = emptyList(),
)

data class EbookContent(
    val meta: EbookContentMeta?,
    val cover: EbookCover?,
    val pages: List<EbookContentPage>,
)

data class EbookContentMeta(
    val title: String?,
    val subtitle: String?,
    val author: String?,
    val pageCount: Int?,
    val language: String?,
)

data class EbookCover(
    val title: String?,
    val backgroundColor: String?,
    val thumbnailHint: String?,
)

data class EbookContentPage(
    val pageNumber: Int?,
    val type: String?,
    val layout: String?,
    val sequence: Int?,
    val title: String?,
    val subtitle: String?,
    val text: String?,
    val imageUrl: String?,
    val caption: String?,
    val quote: String?,
)

data class EbookGenerationResult(
    val ebookId: String,
    val status: EbookStatus,
)

data class PresignedUploadUrl(
    val presignedUrl: String,
    val fileKey: String,
)

enum class EbookType(
    val apiValue: String,
) {
    Image("image"),
    Video("video"),
    Ebook("ebook"),
    Unknown("unknown");

    companion object {
        fun fromApiValue(value: String?): EbookType =
            entries.firstOrNull { type -> type.apiValue.equals(value, ignoreCase = true) }
                ?: Unknown
    }
}

enum class EbookStatus(
    val apiValue: String,
) {
    Processing("PROCESSING"),
    Completed("COMPLETED"),
    Failed("FAILED");

    companion object {
        fun fromApiValue(value: String?): EbookStatus =
            entries.firstOrNull { status -> status.apiValue.equals(value, ignoreCase = true) }
                ?: Processing
    }
}
