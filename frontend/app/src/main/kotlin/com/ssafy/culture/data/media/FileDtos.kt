package com.ssafy.culture.data.media

import com.google.gson.annotations.SerializedName

data class PresignedUrlRequestDto(
    @SerializedName("fileName")
    val fileName: String,
    @SerializedName("contentType")
    val contentType: String,
)

data class PresignedUrlDto(
    @SerializedName("presignedUrl")
    val presignedUrl: String?,
    @SerializedName("fileKey")
    val fileKey: String?,
    @SerializedName("publicUrl")
    val publicUrl: String?,
)
