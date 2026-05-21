package com.ssafy.culture.data.ebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.di.GeminiImageOkHttpClient
import com.ssafy.culture.domain.model.EbookResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class EbookFileRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    @param:GeminiImageOkHttpClient private val imageClient: OkHttpClient,
) {
    suspend fun getLocalPdfFile(result: EbookResult): File = withContext(Dispatchers.IO) {
        val file = File(getEbookCacheDir(), result.toPdfFileName())
        if (file.exists() && file.length() > 0L) return@withContext file
        if (MockApiConfig.enabled || result.fileUrl.contains(MockHostName)) {
            writeMockPdf(file = file, result = result)
            return@withContext file
        }
        val url: String = result.fileUrl.takeIf(String::isHttpUrl)
            ?: error("전자책 다운로드 주소가 아직 준비되지 않았어요.")
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        downloadPdf(url = url, destination = tempFile)
        replaceCachedPdf(tempFile = tempFile, destination = file)
        file
    }

    fun getShareUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )

    suspend fun downloadImageBitmap(
        imageUrl: String,
        maxDimension: Int,
    ): Bitmap = withContext(Dispatchers.IO) {
        val url: String = imageUrl.takeIf(String::isHttpUrl)
            ?: error("Image download URL is not ready.")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        imageClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Image download failed. (${response.code})")
            }
            val imageBytes: ByteArray = response.body.bytes()
            val bitmap: Bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                ?: error("Image file could not be decoded.")
            bitmap.scaleDown(maxDimension)
        }
    }

    suspend fun saveCapturedPdf(
        bookId: String,
        title: String,
        spreadBitmaps: List<Bitmap>,
    ): File = withContext(Dispatchers.IO) {
        check(spreadBitmaps.isNotEmpty()) {
            "Captured eBook pages are empty."
        }
        val file = File(getEbookCacheDir(), createCapturedPdfFileName(bookId = bookId, title = title))
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.delete()
        val document = PdfDocument()
        try {
            spreadBitmaps.forEachIndexed { index, bitmap ->
                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width,
                    bitmap.height,
                    index + 1,
                ).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawColor(android.graphics.Color.BLACK)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
            }
            tempFile.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        replaceCachedPdf(tempFile = tempFile, destination = file)
        file
    }

    private fun getEbookCacheDir(): File =
        File(context.cacheDir, EbookCacheDirName).apply {
            mkdirs()
        }

    private fun downloadPdf(
        url: String,
        destination: File,
    ) {
        destination.delete()
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("전자책 파일을 내려받지 못했어요. (${response.code})")
            }
            destination.outputStream().use { outputStream ->
                response.body.byteStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
    }

    private fun replaceCachedPdf(
        tempFile: File,
        destination: File,
    ) {
        check(tempFile.exists() && tempFile.length() > 0L) {
            "Downloaded eBook file is empty."
        }
        destination.delete()
        if (tempFile.renameTo(destination)) return
        tempFile.copyTo(destination, overwrite = true)
        tempFile.delete()
        check(destination.length() > 0L) {
            "Downloaded eBook file is empty."
        }
    }

    private fun writeMockPdf(
        file: File,
        result: EbookResult,
    ) {
        val pageCount: Int = result.metadata?.pageCount?.coerceAtLeast(1) ?: MockPdfPageCount
        val title: String = result.title.ifBlank { "나의 문화 여행 이야기" }
        val document = PdfDocument()
        try {
            repeat(pageCount) { index ->
                val pageInfo = PdfDocument.PageInfo.Builder(MockPdfWidth, MockPdfHeight, index + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(53, 18, 31)
                    textSize = 30f
                    isFakeBoldText = true
                }
                val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(122, 85, 101)
                    textSize = 20f
                }
                canvas.drawColor(android.graphics.Color.rgb(255, 251, 243))
                canvas.drawText(title, 54f, 96f, titlePaint)
                canvas.drawText("${index + 1}번째 페이지", 54f, 146f, bodyPaint)
                canvas.drawText("생성된 eBook 파일 미리보기입니다.", 54f, 188f, bodyPaint)
                canvas.drawText("실제 서버 PDF가 준비되면 이 자리에 표시돼요.", 54f, 230f, bodyPaint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
    }

    private companion object {
        const val EbookCacheDirName = "ebooks"
        const val MockHostName = "cdn.example.com"
        const val MockPdfPageCount = 6
        const val MockPdfWidth = 595
        const val MockPdfHeight = 842
    }
}

private fun createCapturedPdfFileName(
    bookId: String,
    title: String,
): String {
    val rawName: String = bookId.ifBlank { title }.ifBlank { "storybook" }
    val safeName: String = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "${safeName}_landscape.pdf"
}

private fun EbookResult.toPdfFileName(): String {
    val rawName: String = fileId.ifBlank { title }.ifBlank { "ebook" }
    val safeName: String = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "$safeName.pdf"
}

private fun String.isHttpUrl(): Boolean =
    startsWith("http://") || startsWith("https://")

private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
    val largestDimension: Int = max(width, height)
    if (largestDimension <= maxDimension) return this
    val scale: Float = maxDimension.toFloat() / largestDimension.toFloat()
    val targetWidth: Int = (width * scale).roundToInt().coerceAtLeast(1)
    val targetHeight: Int = (height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
}
