package com.ssafy.culture.data.ml

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeritageMissionMatcher @Inject constructor() {
    fun evaluate(
        expectedPlaceTitle: String,
        prediction: HeritagePrediction?,
    ): HeritageMissionValidation {
        if (expectedPlaceTitle.isBlank()) {
            return HeritageMissionValidation.Unknown
        }
        if (prediction == null) {
            return HeritageMissionValidation.Pending
        }
        val acceptedLabels: List<String> = expectedPlaceTitle.toAcceptedLabels()
        if (acceptedLabels.isEmpty()) {
            return HeritageMissionValidation.Unknown
        }
        val matched: Boolean = prediction.topK.any { candidate ->
            candidate.probability >= MinimumProbability &&
                acceptedLabels.any { label -> candidate.labelKo.normalized().contains(label.normalized()) }
        }
        return if (matched) {
            HeritageMissionValidation.Accepted
        } else {
            HeritageMissionValidation.Rejected(
                message = "${expectedPlaceTitle} 사진으로 확인되지 않았어요. 다시 촬영해 주세요.",
            )
        }
    }

    private fun String.toAcceptedLabels(): List<String> =
        when {
            contains("불국사") -> listOf("불국사", "다보탑", "석가탑", "백운교", "대웅전")
            contains("다보탑") -> listOf("다보탑", "불국사 다보탑")
            contains("석가탑") -> listOf("석가탑", "불국사 석가탑")
            contains("백운교") -> listOf("백운교", "불국사 백운교")
            contains("대웅전") -> listOf("대웅전", "불국사 대웅전")
            contains("천마총") -> listOf("천마총", "천마총 금관")
            contains("동궁") || contains("월지") -> listOf("동궁과 월지", "월지")
            contains("석빙고") -> listOf("석빙고")
            contains("첨성대") -> listOf("첨성대")
            contains("향교") -> listOf("향교")
            contains("봉황대") -> listOf("봉황대")
            contains("에밀레종") || contains("성덕대왕신종") -> listOf("에밀레종", "성덕대왕신종")
            contains("고선사지") || contains("삼층석탑") -> listOf("고선사지", "삼층석탑")
            contains("고분") || contains("대릉원") -> listOf("기타 고분", "고분")
            else -> emptyList()
        }

    private fun String.normalized(): String =
        replace(Regex("[\\s()·.-]"), "")
            .lowercase()

    private companion object {
        const val MinimumProbability = 0.55f
    }
}

sealed interface HeritageMissionValidation {
    data object Pending : HeritageMissionValidation
    data object Accepted : HeritageMissionValidation
    data object Unknown : HeritageMissionValidation
    data class Rejected(
        val message: String,
    ) : HeritageMissionValidation
}
