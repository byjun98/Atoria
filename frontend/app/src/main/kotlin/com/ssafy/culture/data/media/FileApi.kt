package com.ssafy.culture.data.media

import retrofit2.http.Body
import retrofit2.http.POST

interface FileApi {
    @POST("files/presigned-url")
    suspend fun getPresignedUrl(
        @Body request: PresignedUrlRequestDto,
    ): PresignedUrlDto
}
