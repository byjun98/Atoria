package com.ssafy.culture.data.ebook

import com.ssafy.culture.data.remote.ApiResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface EbookApi {
    @POST("ebooks")
    suspend fun requestEbookGeneration(
        @Header("Authorization") authorization: String?,
        @Body request: EbookGenerationRequestDto,
    ): ApiResponse<EbookGenerationResponseDto>

    @GET("ebooks")
    suspend fun getEbooks(
        @Header("Authorization") authorization: String?,
    ): ApiResponse<List<EbookDto>>

    @GET("ebooks/{ebookId}")
    suspend fun getEbookDetail(
        @Header("Authorization") authorization: String?,
        @Path("ebookId") ebookId: String,
    ): ApiResponse<EbookDto>

    @POST("files/presigned-url")
    suspend fun getPresignedUrl(
        @Header("Authorization") authorization: String?,
        @Body request: PresignedUrlRequestDto,
    ): PresignedUrlDto
}
