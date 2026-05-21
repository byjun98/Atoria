package com.ssafy.culture.data.ebook

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GeminiImageApi {
    @POST("v1beta/models/gemini-2.5-flash-image:generateContent")
    suspend fun generateFairyTaleImage(
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiGenerateContentRequestDto,
    ): GeminiGenerateContentResponseDto
}
