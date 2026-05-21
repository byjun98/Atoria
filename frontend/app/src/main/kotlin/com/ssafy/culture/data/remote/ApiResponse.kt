package com.ssafy.culture.data.remote

data class ApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T?,
)

data class PageInfoDto(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
)

data class PagedApiResponse<T>(
    val success: Boolean,
    val code: Int,
    val message: String,
    val data: T?,
    val pageInfo: PageInfoDto?,
)
