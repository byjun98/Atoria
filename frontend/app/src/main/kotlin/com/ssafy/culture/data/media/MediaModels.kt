package com.ssafy.culture.data.media

import android.net.Uri

data class MediaCaptureResult(
    val imageUri: Uri,
    val videoUri: Uri? = null,
    val videoDurationMillis: Long? = null,
)

data class UploadedMissionMedia(
    val fileUrl: String,
    val mediaType: MissionMediaType,
    val localUri: Uri,
    val videoUri: Uri? = null,
    val videoDurationMillis: Long? = null,
)

enum class MissionMediaType(
    val apiValue: String,
) {
    Image("IMAGE"),
    Video("VIDEO"),
}
