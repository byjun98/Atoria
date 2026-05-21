package com.ssafy.culture.data.ml

// AI_MODEL_INTEGRATION: Result model for local heritage classifier predictions.
data class HeritagePrediction(
    val classSlug: String,
    val labelKo: String,
    val probability: Float,
    val topK: List<HeritagePredictionCandidate>,
)

data class HeritagePredictionCandidate(
    val classSlug: String,
    val labelKo: String,
    val probability: Float,
)
