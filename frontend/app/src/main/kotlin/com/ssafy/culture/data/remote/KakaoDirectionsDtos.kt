package com.ssafy.culture.data.remote

import com.google.gson.annotations.SerializedName

data class KakaoDirectionsResponse(
    val routes: List<KakaoDirectionRouteDto>?,
)

data class KakaoDirectionRouteDto(
    @SerializedName("result_code")
    val resultCode: Int?,
    @SerializedName("result_msg")
    val resultMessage: String?,
    val summary: KakaoDirectionSummaryDto?,
    val sections: List<KakaoDirectionSectionDto>?,
)

data class KakaoDirectionSummaryDto(
    val distance: Int?,
    val duration: Int?,
)

data class KakaoDirectionSectionDto(
    val distance: Int?,
    val duration: Int?,
    val roads: List<KakaoDirectionRoadDto>?,
)

data class KakaoDirectionRoadDto(
    val distance: Int?,
    val duration: Int?,
    val vertexes: List<Double>?,
)
