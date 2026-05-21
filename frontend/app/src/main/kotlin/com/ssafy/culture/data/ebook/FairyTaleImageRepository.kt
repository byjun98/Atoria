package com.ssafy.culture.data.ebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKit
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.ssafy.culture.BuildConfig
import com.ssafy.culture.data.dev.MockApiConfig
import com.ssafy.culture.di.GeminiImageOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class FairyTaleProtagonistCandidate(
    val normalizedLeft: Float,
    val normalizedTop: Float,
    val normalizedRight: Float,
    val normalizedBottom: Float,
    val score: Float = 0f,
) {
    companion object {
        fun around(normalizedX: Float, normalizedY: Float): FairyTaleProtagonistCandidate {
            val width = ManualCandidateWidthRatio
            val height = ManualCandidateHeightRatio
            val left = (normalizedX - width / 2f).coerceIn(0f, 1f - width)
            val top = (normalizedY - height * ManualCandidateTopBiasRatio)
                .coerceIn(0f, 1f - height)
            return FairyTaleProtagonistCandidate(
                normalizedLeft = left,
                normalizedTop = top,
                normalizedRight = left + width,
                normalizedBottom = top + height,
                score = 1f,
            )
        }

        private const val ManualCandidateWidthRatio = 0.28f
        private const val ManualCandidateHeightRatio = 0.62f
        private const val ManualCandidateTopBiasRatio = 0.38f
    }
}

data class FairyTaleProtagonistSelectionPreview(
    val previewBitmap: Bitmap,
    val candidates: List<FairyTaleProtagonistCandidate>,
    val selectedIndex: Int?,
)

private data class SelfieSegmentationMaskSnapshot(
    val width: Int,
    val height: Int,
    val confidences: FloatArray,
)

private const val SelfieMaskConfidenceCutoff = 0.42f
private const val SelfieMaskConfidenceSoftness = 0.28f
private const val SelfieMaskMaxAlphaChannel = 255f

@Singleton
class FairyTaleImageRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val geminiImageApi: GeminiImageApi,
    @param:GeminiImageOkHttpClient private val imageClient: OkHttpClient,
) {
    private var isMlKitInitialized: Boolean = false

    suspend fun loadSavedFairyTaleImage(
        bookId: String,
        pageId: String,
        sourceImageUrl: String,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file: File = getFairyTaleImageFile(
            bookId = bookId,
            pageId = pageId,
            sourceImageUrl = sourceImageUrl,
        )
        if (!file.exists() || file.length() <= 0L) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun saveFairyTaleImage(
        bookId: String,
        pageId: String,
        sourceImageUrl: String,
        bitmap: Bitmap,
    ) {
        withContext(Dispatchers.IO) {
            val file: File = getFairyTaleImageFile(
                bookId = bookId,
                pageId = pageId,
                sourceImageUrl = sourceImageUrl,
            )
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "${file.name}.tmp")
            tempFile.outputStream().use { outputStream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, outputStream)) {
                    "Fairy tale image could not be encoded."
                }
            }
            replaceCachedImage(tempFile = tempFile, destination = file)
        }
    }

    suspend fun createFairyTaleImage(
        sourceImageUrl: String,
        bookTitle: String,
        pageTitle: String,
        pageText: String,
        protagonistCandidate: FairyTaleProtagonistCandidate? = null,
    ): Bitmap = withContext(Dispatchers.IO) {
        if (MockApiConfig.enabled) {
            return@withContext createMockFairyTaleBitmap()
        }
        val apiKey: String = BuildConfig.GMS_API_KEY.takeIf(String::isNotBlank)
            ?: error("GMS API key is missing. Add gms.api.key to local.properties.")
        val sourceBitmap: Bitmap = downloadSourceBitmap(sourceImageUrl)
        val sourceImage: GeminiInlineDataDto = sourceBitmap.toJpegInlineData()
        val protagonistMask: SelfieSegmentationMaskSnapshot? = protagonistCandidate
            ?.let { sourceBitmap.createSelfieSegmentationMaskSnapshot() }
        val styleReferenceImage: GeminiInlineDataDto = loadStyleReferenceImageAsInlineData()
        val firstBitmap: Bitmap = requestFairyTaleBitmap(
            apiKey = apiKey,
            sourceImage = sourceImage,
            styleReferenceImage = styleReferenceImage,
            bookTitle = bookTitle,
            pageTitle = pageTitle,
            pageText = pageText,
            hasRealProtagonistOverlay = protagonistCandidate != null,
            forceFullRedraw = false,
        )
        val firstResult: Bitmap = firstBitmap.withSelectedProtagonistOverlay(
            sourceBitmap = sourceBitmap,
            candidate = protagonistCandidate,
            protagonistMask = protagonistMask,
        )
        if (!firstBitmap.isTooSimilarTo(sourceBitmap)) {
            if (firstResult !== firstBitmap) firstBitmap.recycle()
            return@withContext firstResult
        }
        if (firstResult !== firstBitmap) firstResult.recycle()
        firstBitmap.recycle()
        val retryBitmap: Bitmap = requestFairyTaleBitmap(
            apiKey = apiKey,
            sourceImage = sourceImage,
            styleReferenceImage = styleReferenceImage,
            bookTitle = bookTitle,
            pageTitle = pageTitle,
            pageText = pageText,
            hasRealProtagonistOverlay = protagonistCandidate != null,
            forceFullRedraw = true,
        )
        val retryResult: Bitmap = retryBitmap.withSelectedProtagonistOverlay(
            sourceBitmap = sourceBitmap,
            candidate = protagonistCandidate,
            protagonistMask = protagonistMask,
        )
        if (retryBitmap.isTooSimilarTo(sourceBitmap)) {
            if (retryResult !== retryBitmap) retryResult.recycle()
            retryBitmap.recycle()
            error("Generated fairy tale image was too similar to the source photo.")
        }
        if (retryResult !== retryBitmap) retryBitmap.recycle()
        retryResult
    }

    private suspend fun requestFairyTaleBitmap(
        apiKey: String,
        sourceImage: GeminiInlineDataDto,
        styleReferenceImage: GeminiInlineDataDto,
        bookTitle: String,
        pageTitle: String,
        pageText: String,
        hasRealProtagonistOverlay: Boolean,
        forceFullRedraw: Boolean,
    ): Bitmap {
        val response: GeminiGenerateContentResponseDto = geminiImageApi.generateFairyTaleImage(
            apiKey = apiKey,
            request = GeminiGenerateContentRequestDto(
                contents = listOf(
                    GeminiContentDto(
                        parts = buildFairyTaleRequestParts(
                            sourceImage = sourceImage,
                            styleReferenceImage = styleReferenceImage,
                            bookTitle = bookTitle,
                            pageTitle = pageTitle,
                            pageText = pageText,
                            hasRealProtagonistOverlay = hasRealProtagonistOverlay,
                            forceFullRedraw = forceFullRedraw,
                        ),
                    ),
                ),
            ),
        )
        return response.decodeFirstImageBitmap()
    }

    suspend fun prepareProtagonistSelection(
        sourceImageUrl: String,
    ): FairyTaleProtagonistSelectionPreview = withContext(Dispatchers.IO) {
        val sourceBitmap: Bitmap = downloadSourceBitmap(sourceImageUrl)
        val candidates: List<FairyTaleProtagonistCandidate> =
            detectProtagonistCandidates(sourceBitmap)
                .ifEmpty { listOf(createCenteredProtagonistCandidate()) }
        FairyTaleProtagonistSelectionPreview(
            previewBitmap = sourceBitmap,
            candidates = candidates,
            selectedIndex = candidates.indices.firstOrNull(),
        )
    }

    private fun downloadSourceBitmap(sourceImageUrl: String): Bitmap {
        val request: Request = Request.Builder()
            .url(sourceImageUrl)
            .get()
            .build()
        imageClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Source image download failed: ${response.code}" }
            val sourceBytes: ByteArray = response.body.bytes()
            return sourceBytes.toOrientedBitmap()
        }
    }

    private fun loadStyleReferenceImageAsInlineData(): GeminiInlineDataDto =
        context.assets.open(StyleReferenceAssetName).use { inputStream ->
            inputStream.readBytes().toOrientedBitmap().toJpegInlineData()
        }

    private fun ByteArray.toOrientedBitmap(): Bitmap {
        val orientation: Int = readExifOrientation()
        val bitmap: Bitmap = BitmapFactory.decodeByteArray(this, 0, size)
            ?: error("Source image could not be decoded.")
        val orientedBitmap: Bitmap = bitmap.applyExifOrientation(orientation)
        return orientedBitmap.scaleDown(SourceImageMaxDimension)
    }

    private fun Bitmap.toJpegInlineData(): GeminiInlineDataDto {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, SourceImageJpegQuality, outputStream)
        return GeminiInlineDataDto(
            mimeType = JpegMimeType,
            data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP),
        )
    }

    private fun Bitmap.withRealProtagonistOverlay(
        sourceBitmap: Bitmap,
        candidate: FairyTaleProtagonistCandidate,
        protagonistMask: SelfieSegmentationMaskSnapshot?,
    ): Bitmap {
        val outputBitmap: Bitmap = copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(outputBitmap)
        val sourceRect: RectF = candidate.toPixelRect(sourceBitmap.width, sourceBitmap.height).expanded(
            expandX = sourceBitmap.width * ProtagonistOverlayPaddingWidthRatio,
            expandY = sourceBitmap.height * ProtagonistOverlayPaddingHeightRatio,
            maxWidth = sourceBitmap.width,
            maxHeight = sourceBitmap.height,
        )
        val targetRect: RectF = candidate.toPixelRect(width, height).expanded(
            expandX = width * ProtagonistOverlayPaddingWidthRatio,
            expandY = height * ProtagonistOverlayPaddingHeightRatio,
            maxWidth = width,
            maxHeight = height,
        )
        val overlayBitmap: Bitmap = sourceBitmap.createProtagonistOverlay(
            sourceRect = sourceRect,
            protagonistMask = protagonistMask,
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
        }
        canvas.drawBitmap(overlayBitmap, null, targetRect, paint)
        overlayBitmap.recycle()
        return outputBitmap
    }

    private fun Bitmap.withSelectedProtagonistOverlay(
        sourceBitmap: Bitmap,
        candidate: FairyTaleProtagonistCandidate?,
        protagonistMask: SelfieSegmentationMaskSnapshot?,
    ): Bitmap =
        candidate?.let { selectedCandidate ->
            withRealProtagonistOverlay(
                sourceBitmap = sourceBitmap,
                candidate = selectedCandidate,
                protagonistMask = protagonistMask,
            )
        } ?: this

    private fun Bitmap.createProtagonistOverlay(
        sourceRect: RectF,
        protagonistMask: SelfieSegmentationMaskSnapshot?,
    ): Bitmap =
        protagonistMask
            ?.let { mask -> createSegmentedProtagonistOverlay(sourceRect, mask) }
            ?: createRoundedProtagonistOverlay(sourceRect)

    private fun Bitmap.createSegmentedProtagonistOverlay(
        sourceRect: RectF,
        protagonistMask: SelfieSegmentationMaskSnapshot,
    ): Bitmap {
        val cropLeft: Int = sourceRect.left.roundToInt().coerceIn(0, width - 1)
        val cropTop: Int = sourceRect.top.roundToInt().coerceIn(0, height - 1)
        val cropRight: Int = sourceRect.right.roundToInt().coerceIn(cropLeft + 1, width)
        val cropBottom: Int = sourceRect.bottom.roundToInt().coerceIn(cropTop + 1, height)
        val cropWidth: Int = cropRight - cropLeft
        val cropHeight: Int = cropBottom - cropTop
        val croppedBitmap: Bitmap = Bitmap.createBitmap(this, cropLeft, cropTop, cropWidth, cropHeight)
        val pixels: IntArray = IntArray(cropWidth * cropHeight)
        var visiblePixelCount: Int = 0
        croppedBitmap.getPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        for (y in 0 until cropHeight) {
            for (x in 0 until cropWidth) {
                val index: Int = y * cropWidth + x
                val alpha: Int = protagonistMask.alphaAt(
                    sourceX = cropLeft + x,
                    sourceY = cropTop + y,
                    sourceWidth = width,
                    sourceHeight = height,
                )
                if (alpha > MinimumVisibleAlpha) visiblePixelCount += 1
                pixels[index] = (pixels[index] and RgbMask) or (alpha shl AlphaShift)
            }
        }
        if (visiblePixelCount < cropWidth * cropHeight * MinimumSegmentedOverlayCoverageRatio) {
            croppedBitmap.recycle()
            return createRoundedProtagonistOverlay(sourceRect)
        }
        val overlayBitmap: Bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        overlayBitmap.setPixels(pixels, 0, cropWidth, 0, 0, cropWidth, cropHeight)
        croppedBitmap.recycle()
        return overlayBitmap
    }

    private fun Bitmap.createRoundedProtagonistOverlay(sourceRect: RectF): Bitmap {
        val cropLeft: Int = sourceRect.left.roundToInt().coerceIn(0, width - 1)
        val cropTop: Int = sourceRect.top.roundToInt().coerceIn(0, height - 1)
        val cropRight: Int = sourceRect.right.roundToInt().coerceIn(cropLeft + 1, width)
        val cropBottom: Int = sourceRect.bottom.roundToInt().coerceIn(cropTop + 1, height)
        val cropWidth: Int = cropRight - cropLeft
        val cropHeight: Int = cropBottom - cropTop
        val croppedBitmap: Bitmap = Bitmap.createBitmap(this, cropLeft, cropTop, cropWidth, cropHeight)
        val overlayBitmap: Bitmap = Bitmap.createBitmap(cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(overlayBitmap)
        val feather: Float = (min(cropWidth, cropHeight) * ProtagonistOverlayFeatherRatio)
            .coerceAtLeast(MinimumOverlayFeatherPx)
        val radius: Float = min(cropWidth, cropHeight) * ProtagonistOverlayCornerRadiusRatio
        val maskRect: RectF = RectF(feather, feather, cropWidth - feather, cropHeight - feather)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            maskFilter = BlurMaskFilter(feather, BlurMaskFilter.Blur.NORMAL)
        }
        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        }
        canvas.saveLayer(0f, 0f, cropWidth.toFloat(), cropHeight.toFloat(), null)
        canvas.drawRoundRect(maskRect, radius, radius, maskPaint)
        canvas.drawBitmap(croppedBitmap, 0f, 0f, imagePaint)
        canvas.restore()
        croppedBitmap.recycle()
        return overlayBitmap
    }

    private fun Bitmap.createSelfieSegmentationMaskSnapshot(): SelfieSegmentationMaskSnapshot? {
        if (!canUseMlKitVision()) return null
        val options: SelfieSegmenterOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        return runCatching {
            val segmenter = Segmentation.getClient(options)
            try {
                val mask: SegmentationMask = Tasks.await(segmenter.process(InputImage.fromBitmap(this, 0)))
                val confidences: FloatArray = FloatArray(mask.width * mask.height)
                mask.buffer.rewind()
                mask.buffer.asFloatBuffer().get(confidences)
                SelfieSegmentationMaskSnapshot(
                    width = mask.width,
                    height = mask.height,
                    confidences = confidences,
                )
            } finally {
                segmenter.close()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "ML Kit selfie segmentation failed. Falling back to rounded protagonist crop.", throwable)
        }.getOrNull()
    }

    private fun ByteArray.readExifOrientation(): Int =
        runCatching {
            ByteArrayInputStream(this).use { inputStream ->
                ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    private fun GeminiGenerateContentResponseDto.decodeFirstImageBitmap(): Bitmap {
        val imagePart: GeminiResponseInlineDataDto = candidates.orEmpty()
            .asSequence()
            .flatMap { candidate -> candidate.content?.parts.orEmpty().asSequence() }
            .mapNotNull(GeminiResponsePartDto::inlineData)
            .firstOrNull { inlineData -> inlineData.data?.isNotBlank() == true }
            ?: error("Gemini image response did not include image data.")
        val imageBase64: String = imagePart.data ?: error("Gemini image response data was empty.")
        val imageBytes: ByteArray = Base64.decode(imageBase64, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: error("Generated image could not be decoded.")
    }

    private fun Bitmap.scaleDown(maxDimension: Int): Bitmap {
        val largestDimension: Int = max(width, height)
        if (largestDimension <= maxDimension) return this
        val scale: Float = maxDimension.toFloat() / largestDimension.toFloat()
        val targetWidth: Int = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight: Int = (height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
    }

    private fun Bitmap.isTooSimilarTo(sourceBitmap: Bitmap): Boolean =
        averageRgbDifference(sourceBitmap) < MinimumFairyTaleDifferenceScore

    private fun Bitmap.averageRgbDifference(other: Bitmap): Float {
        val sampleA = Bitmap.createScaledBitmap(this, SimilaritySampleSize, SimilaritySampleSize, true)
        val sampleB = Bitmap.createScaledBitmap(other, SimilaritySampleSize, SimilaritySampleSize, true)
        try {
            var totalDifference = 0L
            for (y in 0 until SimilaritySampleSize) {
                for (x in 0 until SimilaritySampleSize) {
                    val pixelA = sampleA.getPixel(x, y)
                    val pixelB = sampleB.getPixel(x, y)
                    totalDifference += abs(Color.red(pixelA) - Color.red(pixelB))
                    totalDifference += abs(Color.green(pixelA) - Color.green(pixelB))
                    totalDifference += abs(Color.blue(pixelA) - Color.blue(pixelB))
                }
            }
            return totalDifference.toFloat() /
                (SimilaritySampleSize * SimilaritySampleSize * SimilarityRgbChannels * MaxColorChannel)
        } finally {
            if (sampleA !== this) sampleA.recycle()
            if (sampleB !== other) sampleB.recycle()
        }
    }

    private fun Bitmap.applyExifOrientation(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return this
        }
        val rotatedBitmap: Bitmap = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        recycle()
        return rotatedBitmap
    }

    private suspend fun detectProtagonistCandidates(
        bitmap: Bitmap,
    ): List<FairyTaleProtagonistCandidate> = withContext(Dispatchers.Default) {
        if (!canUseMlKitVision()) return@withContext emptyList()
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(MinFaceSizeRatio)
            .build()
        runCatching {
            val detector = FaceDetection.getClient(options)
            try {
                val faces: List<Face> = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0)))
                faces
                    .asSequence()
                    .mapNotNull { face -> face.toProtagonistCandidate(bitmap.width, bitmap.height) }
                    .sortedByDescending(FairyTaleProtagonistCandidate::score)
                    .take(MaxProtagonistCandidateCount)
                    .toList()
            } finally {
                detector.close()
            }
        }.onFailure { throwable ->
            Log.w(TAG, "ML Kit face detection failed. Falling back to centered protagonist.", throwable)
        }.getOrDefault(emptyList())
    }

    private fun canUseMlKitVision(): Boolean {
        if (!isMlKitVisionRuntimeSupported()) return false
        if (isMlKitInitialized) return true
        return runCatching {
            MlKit.initialize(context)
            isMlKitInitialized = true
        }.onFailure { throwable ->
            Log.w(TAG, "ML Kit initialization failed. Falling back to manual protagonist selection.", throwable)
        }.isSuccess
    }

    private fun isMlKitVisionRuntimeSupported(): Boolean {
        val isX86Emulator = Build.SUPPORTED_ABIS.any { abi ->
            abi.equals("x86", ignoreCase = true) || abi.equals("x86_64", ignoreCase = true)
        }
        if (isX86Emulator) {
            Log.w(TAG, "Skipping ML Kit face detection on x86 emulator.")
            return false
        }
        return true
    }

    private fun createCenteredProtagonistCandidate(): FairyTaleProtagonistCandidate =
        FairyTaleProtagonistCandidate.around(
            normalizedX = CenteredProtagonistX,
            normalizedY = CenteredProtagonistY,
        )

    private fun buildFairyTaleRequestParts(
        sourceImage: GeminiInlineDataDto,
        styleReferenceImage: GeminiInlineDataDto,
        bookTitle: String,
        pageTitle: String,
        pageText: String,
        hasRealProtagonistOverlay: Boolean,
        forceFullRedraw: Boolean,
    ): List<GeminiPartDto> =
        buildList {
            add(
                GeminiPartDto(
                    text = buildFairyTalePrompt(
                        bookTitle = bookTitle,
                        pageTitle = pageTitle,
                        pageText = pageText,
                        hasRealProtagonistOverlay = hasRealProtagonistOverlay,
                        forceFullRedraw = forceFullRedraw,
                    ),
                ),
            )
            add(GeminiPartDto(inlineData = sourceImage))
            add(GeminiPartDto(inlineData = styleReferenceImage))
        }

    private fun buildFairyTalePrompt(
        bookTitle: String,
        pageTitle: String,
        pageText: String,
        hasRealProtagonistOverlay: Boolean,
        forceFullRedraw: Boolean,
    ): String {
        val imageRoleInstruction: String = """
            Image 1 is the content reference.
            Image 2 is only the style reference.
            """.trimIndent()
        val protagonistInstruction: String = if (hasRealProtagonistOverlay) {
            """
            The app will place the selected protagonist back on top of this result using the original photo.
            Focus this generation on turning everything around the protagonist into storybook artwork.
            Keep the main foreground person's position and silhouette area compositionally natural, but do not try to preserve the face exactly in this generated layer.
            Do not add white masks, blank patches, glow, paint spills, or occluding shapes over any face.
            Other people should stay small, low-detail, and secondary in the illustrated background.
            """.trimIndent()
        } else {
            """
            If there are visible people, redraw them as simple storybook characters with clear, friendly faces and broad clothing color cues.
            If there are tourists or passersby, keep them small, low-detail, and secondary in the background.
            """.trimIndent()
        }
        val redrawInstruction: String = if (forceFullRedraw) {
            """
            The previous result was too similar to the source photo.
            This attempt must be a much stronger full redraw.
            Change every visible surface into painted storybook artwork: grass, stone, pavement, people, shadows, sky, signs, and ropes.
            Use simplified shapes, visible brush texture, softer edges, and non-photographic color transitions throughout the entire image.
            """.trimIndent()
        } else {
            ""
        }
        return """
        $imageRoleInstruction

        Create a new hand-painted Korean children's picture-book illustration inspired by Image 1.

        Important priority:
        1. The output must look like a newly painted illustration.
        2. Preserve the scene layout only approximately.
        3. Do not preserve photographic realism.
        4. If exact photo preservation conflicts with the illustration style, prioritize the illustration style.
        $redrawInstruction

        Use Image 1 as the scene reference, not as pixels to keep.
        Recreate the landmark, ground, sky, ropes, signs, people, shadows, and background as simplified painted shapes.
        The final result should not look like a photo filter, a zoomed-out copy, or a lightly edited photo.
        Do not copy the exact pixels, photo texture, lighting, lens sharpness, realistic shadows, or camera-like color from Image 1.
        Redraw the whole scene with visible watercolor, gouache brushwork, pencil outlines, simplified storybook shapes, soft painted shadows, and warm illustrated lighting.
        Keep the cultural heritage place recognizable, but allow painterly simplification and child-friendly stylization.
        Keep identity-sensitive details only as broad visual cues; this is an illustration, not a realistic portrait.
        Use Image 2 only as a style reference: soft watercolor, gouache, pencil outline, warm children's book mood.
        Do not copy Image 2's landmark, child, people, animals, stars, night sky, border, props, or layout.

        If Image 1 has no visible people, do not add any people, characters, faces, bodies, hands, mascots, or animals.
        If Image 1 has visible people, keep only those people and do not add extra limbs or extra hands.
        $protagonistInstruction
        Do not add readable text, captions, logos, or watermarks.

        Book: $bookTitle
        Page: $pageTitle
        Context for mood only: $pageText
        """.trimIndent()
    }

    private fun createMockFairyTaleBitmap(): Bitmap {
        val bitmap: Bitmap = Bitmap.createBitmap(MockBitmapSize, MockBitmapSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(Color.rgb(255, 235, 194))
        paint.color = Color.rgb(245, 111, 157)
        canvas.drawCircle(280f, 300f, 190f, paint)
        paint.color = Color.rgb(113, 183, 255)
        canvas.drawCircle(760f, 370f, 220f, paint)
        paint.color = Color.rgb(255, 255, 255)
        canvas.drawRoundRect(190f, 610f, 830f, 735f, 44f, 44f, paint)
        return bitmap
    }

    private fun getFairyTaleImageFile(
        bookId: String,
        pageId: String,
        sourceImageUrl: String,
    ): File {
        val bookDir = File(context.filesDir, FairyTaleImageStorageDir)
            .resolve(bookId.toStableHash())
        val imageKey: String = listOf(
            FairyTaleImageCacheVersion,
            bookId,
            pageId,
            sourceImageUrl.substringBefore("?"),
        ).joinToString(separator = "|")
        return bookDir.resolve("${imageKey.toStableHash()}.png")
    }

    private fun replaceCachedImage(
        tempFile: File,
        destination: File,
    ) {
        check(tempFile.exists() && tempFile.length() > 0L) {
            "Fairy tale image storage file is empty."
        }
        destination.delete()
        if (tempFile.renameTo(destination)) return
        tempFile.copyTo(destination, overwrite = true)
        tempFile.delete()
        check(destination.exists() && destination.length() > 0L) {
            "Fairy tale image storage file is empty."
        }
    }

    private fun String.toStableHash(): String {
        val digest: ByteArray = MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val TAG = "FairyTaleImageRepository"
        const val StyleReferenceAssetName = "fairy_tale_style_reference.png"
        const val FairyTaleImageStorageDir = "ebook_fairy_tales"
        const val FairyTaleImageCacheVersion = "v9-real-protagonist-overlay"
        const val SourceImageMaxDimension = 1024
        const val SourceImageJpegQuality = 88
        const val JpegMimeType = "image/jpeg"
        const val PngQuality = 100
        const val MockBitmapSize = 1024
        const val SimilaritySampleSize = 64
        const val SimilarityRgbChannels = 3
        const val MaxColorChannel = 255f
        const val MinimumFairyTaleDifferenceScore = 0.09f
        const val MinFaceSizeRatio = 0.03f
        const val MaxProtagonistCandidateCount = 5
        const val CenteredProtagonistX = 0.5f
        const val CenteredProtagonistY = 0.52f
        const val ProtagonistOverlayPaddingWidthRatio = 0.02f
        const val ProtagonistOverlayPaddingHeightRatio = 0.02f
        const val ProtagonistOverlayCornerRadiusRatio = 0.07f
        const val ProtagonistOverlayFeatherRatio = 0.035f
        const val MinimumOverlayFeatherPx = 1f
        const val MinimumSegmentedOverlayCoverageRatio = 0.03f
        const val MinimumVisibleAlpha = 24
        const val AlphaShift = 24
        const val RgbMask = 0x00FFFFFF
    }
}

private fun SelfieSegmentationMaskSnapshot.alphaAt(
    sourceX: Int,
    sourceY: Int,
    sourceWidth: Int,
    sourceHeight: Int,
): Int {
    val maskX: Int = ((sourceX.toFloat() / sourceWidth.toFloat()) * (width - 1))
        .roundToInt()
        .coerceIn(0, width - 1)
    val maskY: Int = ((sourceY.toFloat() / sourceHeight.toFloat()) * (height - 1))
        .roundToInt()
        .coerceIn(0, height - 1)
    val confidence: Float = confidences[maskY * width + maskX]
    val normalizedAlpha: Float = ((confidence - SelfieMaskConfidenceCutoff) / SelfieMaskConfidenceSoftness)
        .coerceIn(0f, 1f)
    return (normalizedAlpha * SelfieMaskMaxAlphaChannel).roundToInt()
}

private fun Face.toProtagonistCandidate(
    imageWidth: Int,
    imageHeight: Int,
): FairyTaleProtagonistCandidate? {
    val faceRect: Rect = boundingBox
    if (faceRect.width() <= 0 || faceRect.height() <= 0) return null
    val faceCenterX = faceRect.exactCenterX()
    val faceCenterY = faceRect.exactCenterY()
    val bodyWidth = faceRect.width() * FaceToBodyWidthMultiplier
    val bodyRect = RectF(
        faceCenterX - bodyWidth / 2f,
        faceCenterY - faceRect.height() * FaceToBodyTopMultiplier,
        faceCenterX + bodyWidth / 2f,
        faceCenterY + faceRect.height() * FaceToBodyBottomMultiplier,
    ).clipped(imageWidth, imageHeight)
    val faceAreaRatio = (faceRect.width() * faceRect.height()).toFloat() /
        (imageWidth * imageHeight).toFloat()
    val centerDistance = hypot(
        (faceCenterX / imageWidth.toFloat()) - 0.5f,
        (faceCenterY / imageHeight.toFloat()) - 0.42f,
    )
    val centerScore = (1f - (centerDistance / 0.72f)).coerceIn(0f, 1f)
    val sizeScore = (faceAreaRatio * FaceAreaScoreMultiplier).coerceIn(0f, 1f)
    val frontalScore = (
        1f - (
            abs(headEulerAngleY) / MaxHeadEulerAngle +
                abs(headEulerAngleZ) / MaxHeadEulerAngle
            ) / 2f
        ).coerceIn(0f, 1f)
    val score = centerScore * CenterScoreWeight +
        sizeScore * SizeScoreWeight +
        frontalScore * FrontalScoreWeight
    return FairyTaleProtagonistCandidate(
        normalizedLeft = bodyRect.left / imageWidth.toFloat(),
        normalizedTop = bodyRect.top / imageHeight.toFloat(),
        normalizedRight = bodyRect.right / imageWidth.toFloat(),
        normalizedBottom = bodyRect.bottom / imageHeight.toFloat(),
        score = score,
    )
}

private fun FairyTaleProtagonistCandidate.toPixelRect(
    width: Int,
    height: Int,
): RectF =
    RectF(
        normalizedLeft * width,
        normalizedTop * height,
        normalizedRight * width,
        normalizedBottom * height,
    ).clipped(width, height)

private fun RectF.expanded(
    expandX: Float,
    expandY: Float,
    maxWidth: Int,
    maxHeight: Int,
): RectF =
    RectF(
        left - expandX,
        top - expandY,
        right + expandX,
        bottom + expandY,
    ).clipped(maxWidth, maxHeight)

private fun RectF.clipped(
    width: Int,
    height: Int,
): RectF =
    RectF(
        left.coerceIn(0f, width.toFloat()),
        top.coerceIn(0f, height.toFloat()),
        right.coerceIn(0f, width.toFloat()),
        bottom.coerceIn(0f, height.toFloat()),
    )

private const val FaceToBodyWidthMultiplier = 4.4f
private const val FaceToBodyTopMultiplier = 1.0f
private const val FaceToBodyBottomMultiplier = 6.8f
private const val FaceAreaScoreMultiplier = 85f
private const val CenterScoreWeight = 0.46f
private const val SizeScoreWeight = 0.34f
private const val FrontalScoreWeight = 0.2f
private const val MaxHeadEulerAngle = 45f
