package com.ssafy.culture.data.remote

import com.ssafy.culture.BuildConfig
import javax.inject.Inject

class KakaoLocalAuthHeaderProvider @Inject constructor() {
    fun getAuthorizationHeader(): String {
        val restApiKey = getRestApiKey()
        check(restApiKey.isValidKakaoKey()) {
            "Kakao REST API key is not configured correctly. Check kakao.rest.api.key in local.properties."
        }
        return "KakaoAK $restApiKey"
    }

    // Reserved hook for the optional Kakao-User-Agent header (e.g. "android/<버전>").
    // Returns null today; populate when a partner key requires the agent attribution.
    fun getKakaoAgentHeader(): String? = null

    private fun getRestApiKey(): String =
        runCatching {
            BuildConfig::class.java.getField(KAKAO_REST_API_KEY_FIELD).get(null) as? String
        }.getOrNull().orEmpty()

    private fun String.isValidKakaoKey(): Boolean =
        length == KAKAO_APP_KEY_LENGTH

    private companion object {
        const val KAKAO_REST_API_KEY_FIELD = "KAKAO_REST_API_KEY"
        const val KAKAO_APP_KEY_LENGTH = 32
    }
}
