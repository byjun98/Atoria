package com.ssafy.culture.data.ebook

import com.google.gson.annotations.SerializedName

data class GeminiGenerateContentRequestDto(
    val contents: List<GeminiContentDto>,
    @SerializedName("generationConfig")
    val generationConfig: GeminiGenerationConfigDto = GeminiGenerationConfigDto(),
)

data class GeminiGenerationConfigDto(
    @SerializedName("responseModalities")
    val responseModalities: List<String> = listOf("IMAGE"),
)

data class GeminiContentDto(
    val role: String = "user",
    val parts: List<GeminiPartDto>,
)

data class GeminiPartDto(
    val text: String? = null,
    @SerializedName("inline_data")
    val inlineData: GeminiInlineDataDto? = null,
)

data class GeminiInlineDataDto(
    @SerializedName("mime_type")
    val mimeType: String,
    val data: String,
)

data class GeminiGenerateContentResponseDto(
    val candidates: List<GeminiCandidateDto>? = null,
)

data class GeminiCandidateDto(
    val content: GeminiResponseContentDto? = null,
)

data class GeminiResponseContentDto(
    val parts: List<GeminiResponsePartDto>? = null,
)

data class GeminiResponsePartDto(
    val text: String? = null,
    @SerializedName(value = "inlineData", alternate = ["inline_data"])
    val inlineData: GeminiResponseInlineDataDto? = null,
)

data class GeminiResponseInlineDataDto(
    @SerializedName(value = "mimeType", alternate = ["mime_type"])
    val mimeType: String? = null,
    val data: String? = null,
)
