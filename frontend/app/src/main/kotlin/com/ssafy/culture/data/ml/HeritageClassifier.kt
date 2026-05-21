package com.ssafy.culture.data.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession

// AI_MODEL_INTEGRATION: Loads the bundled ONNX model and runs local image classification.
@Singleton
class HeritageClassifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val environment: OrtEnvironment by lazy {
        OrtEnvironment.getEnvironment()
    }
    private val session: OrtSession by lazy {
        val modelBytes = context.assets.open(ModelAssetName).use { inputStream ->
            inputStream.readBytes()
        }
        environment.createSession(modelBytes, OrtSession.SessionOptions())
    }
    private val metadata: ClassifierMetadata by lazy {
        loadMetadata()
    }

    suspend fun classify(
        imageUri: Uri,
        topK: Int = DefaultTopK,
    ): HeritagePrediction = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmap(imageUri)
        val input = bitmap.toNormalizedFloatArray(metadata.imageSize, metadata.mean, metadata.std)
        val shape = longArrayOf(1L, 3L, metadata.imageSize.toLong(), metadata.imageSize.toLong())
        val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape)
        try {
            val result = session.run(mapOf(metadata.inputName to tensor))
            try {
                val probabilities = result[0].value.toProbabilities()
                probabilities.toPrediction(topK)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    private fun loadMetadata(): ClassifierMetadata {
        val preprocessing = JSONObject(context.assets.readText(PreprocessingAssetName))
        val labels = JSONObject(context.assets.readText(LabelAssetName))
        val classesJson = labels.getJSONArray("classes")
        val labelMapJson = labels.getJSONObject("class_labels_ko")
        val classes = List(classesJson.length()) { index ->
            classesJson.getString(index)
        }
        val labelMap = classes.associateWith { classSlug ->
            labelMapJson.optString(classSlug, classSlug)
        }
        return ClassifierMetadata(
            inputName = preprocessing.optString("input_name", "input"),
            imageSize = preprocessing.optInt("image_size", 224),
            mean = preprocessing.getDoubleArray("mean"),
            std = preprocessing.getDoubleArray("std"),
            classes = classes,
            labelMap = labelMap,
        )
    }

    private fun decodeBitmap(imageUri: Uri): Bitmap {
        val bitmap = context.contentResolver.openInputStream(imageUri).use { inputStream ->
            requireNotNull(inputStream) { "Image could not be opened." }
            requireNotNull(BitmapFactory.decodeStream(inputStream)) { "Image could not be decoded." }
        }
        val argbBitmap = bitmap.toArgb8888()
        val orientation = readExifOrientation(imageUri)
        return argbBitmap.applyExifOrientation(orientation)
    }

    private fun readExifOrientation(imageUri: Uri): Int =
        runCatching {
            context.contentResolver.openInputStream(imageUri).use { inputStream ->
                requireNotNull(inputStream) { "Image could not be opened." }
                ExifInterface(inputStream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)

    private fun Bitmap.toArgb8888(): Bitmap {
        if (config == Bitmap.Config.ARGB_8888) {
            return this
        }
        val converted = copy(Bitmap.Config.ARGB_8888, false)
        recycle()
        return converted
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
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        recycle()
        return rotated
    }

    private fun Bitmap.toNormalizedFloatArray(
        imageSize: Int,
        mean: FloatArray,
        std: FloatArray,
    ): FloatArray {
        val cropped = centerCropAndResize(imageSize)
        val pixels = IntArray(imageSize * imageSize)
        cropped.getPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)

        val input = FloatArray(3 * imageSize * imageSize)
        val channelSize = imageSize * imageSize
        pixels.forEachIndexed { index, pixel ->
            val red = ((pixel shr 16) and 0xFF) / 255f
            val green = ((pixel shr 8) and 0xFF) / 255f
            val blue = (pixel and 0xFF) / 255f
            input[index] = (red - mean[0]) / std[0]
            input[channelSize + index] = (green - mean[1]) / std[1]
            input[channelSize * 2 + index] = (blue - mean[2]) / std[2]
        }
        if (cropped !== this) {
            cropped.recycle()
        }
        return input
    }

    private fun Bitmap.centerCropAndResize(imageSize: Int): Bitmap {
        val scale = maxOf(imageSize.toFloat() / width, imageSize.toFloat() / height)
        val scaledWidth = (width * scale).toInt().coerceAtLeast(imageSize)
        val scaledHeight = (height * scale).toInt().coerceAtLeast(imageSize)
        val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
        val left = ((scaledWidth - imageSize) / 2).coerceAtLeast(0)
        val top = ((scaledHeight - imageSize) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, left, top, imageSize, imageSize)
        if (scaled !== this) {
            scaled.recycle()
        }
        return cropped
    }

    private fun FloatArray.toPrediction(topK: Int): HeritagePrediction {
        val safeSize: Int = minOf(size, metadata.classes.size)
        require(safeSize > 0) { "Classifier returned no probabilities." }
        val candidates = (0 until safeSize)
            .sortedByDescending { index -> this[index] }
            .take(topK.coerceAtLeast(1))
            .map { index ->
                val classSlug = metadata.classes[index]
                HeritagePredictionCandidate(
                    classSlug = classSlug,
                    labelKo = metadata.labelMap[classSlug] ?: classSlug,
                    probability = this[index],
                )
            }
        val best = candidates.first()
        return HeritagePrediction(
            classSlug = best.classSlug,
            labelKo = best.labelKo,
            probability = best.probability,
            topK = candidates,
        )
    }

    private data class ClassifierMetadata(
        val inputName: String,
        val imageSize: Int,
        val mean: FloatArray,
        val std: FloatArray,
        val classes: List<String>,
        val labelMap: Map<String, String>,
    )

    private companion object {
        const val ModelAssetName = "gyeongju_heritage_classifier.onnx"
        const val LabelAssetName = "class_labels.json"
        const val PreprocessingAssetName = "preprocessing.json"
        const val DefaultTopK = 3
    }
}

private fun Any.toProbabilities(): FloatArray =
    when (this) {
        is Array<*> -> firstOrNull() as? FloatArray
        is FloatArray -> this
        else -> null
    } ?: error("Classifier output format is unsupported.")

private fun android.content.res.AssetManager.readText(assetName: String): String =
    open(assetName).use { inputStream ->
        inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

private fun JSONObject.getDoubleArray(name: String): FloatArray {
    val jsonArray = getJSONArray(name)
    return FloatArray(jsonArray.length()) { index ->
        jsonArray.getDouble(index).toFloat()
    }
}
