package com.ssafy.culture.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class CultureDto(
    val id: Int,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
)

interface CultureApi {
    @GET("photos")
    suspend fun getCultureItems(
        @Query("_limit") limit: Int = 20,
    ): List<CultureDto>
}

