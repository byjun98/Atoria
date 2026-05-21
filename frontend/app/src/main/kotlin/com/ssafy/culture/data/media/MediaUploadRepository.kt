package com.ssafy.culture.data.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.ssafy.culture.data.dev.MockApiConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit

@Singleton
class MediaUploadRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    retrofit: Retrofit,
) {
    private val fileApi: FileApi = retrofit.create(FileApi::class.java)
    private val s3Client: OkHttpClient = OkHttpClient()

    suspend fun uploadMissionImage(
        storyId: Long,
        chapterId: Long,
        imageUri: Uri,
    ): UploadedMissionMedia = withContext(Dispatchers.IO) {
        val contentType: String = context.contentResolver.getType(imageUri) ?: DefaultImageContentType
        val imageFile: File = resolveLocalImageFile(
            sourceUri = imageUri,
            storyId = storyId,
            chapterId = chapterId,
            contentType = contentType,
        )
        if (MockApiConfig.enabled) {
            return@withContext UploadedMissionMedia(
                fileUrl = "mock://mission-images/$storyId/$chapterId/${imageFile.name}",
                mediaType = MissionMediaType.Image,
                localUri = Uri.fromFile(imageFile),
            )
        }
        val uploadUrl: PresignedUrlDto = fileApi.getPresignedUrl(
            request = PresignedUrlRequestDto(
                fileName = imageFile.name,
                contentType = contentType,
            ),
        )
        val presignedUrl: String = uploadUrl.presignedUrl.orEmpty()
        val fileUrl: String = uploadUrl.toPublicDownloadUrl()
        require(presignedUrl.isNotBlank() && fileUrl.isNotBlank()) {
            "Image upload URL is empty."
        }
        uploadFileToPresignedUrl(
            presignedUrl = presignedUrl,
            file = imageFile,
            contentType = contentType,
        )
        UploadedMissionMedia(
            fileUrl = fileUrl,
            mediaType = MissionMediaType.Image,
            localUri = Uri.fromFile(imageFile),
        )
    }

    suspend fun uploadMissionVideo(
        storyId: Long,
        chapterId: Long,
        videoUri: Uri,
        durationMillis: Long?,
    ): UploadedMissionMedia = withContext(Dispatchers.IO) {
        val contentType: String = context.contentResolver.getType(videoUri) ?: DefaultVideoContentType
        val videoFile: File = resolveLocalMediaFile(
            sourceUri = videoUri,
            storyId = storyId,
            chapterId = chapterId,
            contentType = contentType,
            mediaLabel = "vlog",
            defaultExtension = DefaultVideoExtension,
        )
        if (MockApiConfig.enabled) {
            val localUri = Uri.fromFile(videoFile)
            return@withContext UploadedMissionMedia(
                fileUrl = "mock://mission-videos/$storyId/$chapterId/${videoFile.name}",
                mediaType = MissionMediaType.Video,
                localUri = localUri,
                videoUri = localUri,
                videoDurationMillis = durationMillis,
            )
        }
        val uploadUrl: PresignedUrlDto = fileApi.getPresignedUrl(
            request = PresignedUrlRequestDto(
                fileName = videoFile.name,
                contentType = contentType,
            ),
        )
        val presignedUrl: String = uploadUrl.presignedUrl.orEmpty()
        val fileUrl: String = uploadUrl.toPublicDownloadUrl()
        require(presignedUrl.isNotBlank() && fileUrl.isNotBlank()) {
            "Video upload URL is empty."
        }
        uploadFileToPresignedUrl(
            presignedUrl = presignedUrl,
            file = videoFile,
            contentType = contentType,
        )
        UploadedMissionMedia(
            fileUrl = fileUrl,
            mediaType = MissionMediaType.Video,
            localUri = Uri.fromFile(videoFile),
            videoUri = Uri.fromFile(videoFile),
            videoDurationMillis = durationMillis,
        )
    }

    fun createCameraVideoFile(
        storyId: Long,
        chapterId: Long,
    ): File =
        File(
            context.cacheDir,
            "mission_${storyId}_${chapterId}_${System.currentTimeMillis()}_clip.mp4",
        )

    private fun resolveLocalImageFile(
        sourceUri: Uri,
        storyId: Long,
        chapterId: Long,
        contentType: String,
    ): File {
        if (sourceUri.scheme == ContentResolver.SCHEME_FILE && sourceUri.path != null) {
            return File(requireNotNull(sourceUri.path))
        }
        val extension: String = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentType)
            ?: DefaultImageExtension
        val targetFile = File(
            context.cacheDir,
            "mission_${storyId}_${chapterId}_${System.currentTimeMillis()}.$extension",
        )
        context.contentResolver.openInputStream(sourceUri).use { inputStream ->
            requireNotNull(inputStream) { "Selected image could not be opened." }
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return targetFile
    }

    private fun resolveLocalMediaFile(
        sourceUri: Uri,
        storyId: Long,
        chapterId: Long,
        contentType: String,
        mediaLabel: String,
        defaultExtension: String,
    ): File {
        if (sourceUri.scheme == ContentResolver.SCHEME_FILE && sourceUri.path != null) {
            return File(requireNotNull(sourceUri.path))
        }
        val extension: String = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentType)
            ?: defaultExtension
        val targetFile = File(
            context.cacheDir,
            "mission_${storyId}_${chapterId}_${mediaLabel}_${System.currentTimeMillis()}.$extension",
        )
        context.contentResolver.openInputStream(sourceUri).use { inputStream ->
            requireNotNull(inputStream) { "Selected media could not be opened." }
            targetFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return targetFile
    }

    private fun uploadFileToPresignedUrl(
        presignedUrl: String,
        file: File,
        contentType: String,
    ) {
        val request = Request.Builder()
            .url(presignedUrl)
            .put(file.asRequestBody(contentType.toMediaType()))
            .build()
        s3Client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("File upload failed: ${response.code}")
            }
        }
    }

    private fun PresignedUrlDto.toPublicDownloadUrl(): String =
        publicUrl.orEmpty().ifBlank {
            presignedUrl.orEmpty()
                .substringBefore("?")
                .takeIf { url -> url.isHttpUrl() }
                .orEmpty()
        }.ifBlank {
            fileKey.orEmpty()
        }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

    private companion object {
        const val DefaultImageContentType = "image/jpeg"
        const val DefaultImageExtension = "jpg"
        const val DefaultVideoContentType = "video/mp4"
        const val DefaultVideoExtension = "mp4"
    }
}
