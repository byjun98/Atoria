package com.ssafy.culture.data.dev

import com.ssafy.culture.BuildConfig

object MockApiConfig {
    @Volatile
    var enabled: Boolean = BuildConfig.MOCK_API
}
