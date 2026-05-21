package com.ssafy.culture

import android.app.Application
import android.os.Build
import android.util.Log
import com.kakao.vectormap.KakaoMapSdk
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class CultureApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank() && isKakaoMapRuntimeSupported()) {
            KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
        }
    }

    private fun isKakaoMapRuntimeSupported(): Boolean {
        val isX86Emulator = Build.SUPPORTED_ABIS.any { abi ->
            abi.equals("x86", ignoreCase = true) || abi.equals("x86_64", ignoreCase = true)
        }
        if (isX86Emulator) {
            Log.w(
                TAG,
                "Skipping KakaoMapSdk.init on x86 emulator. Use an ARM device/emulator for map screens.",
            )
            return false
        }
        return true
    }

    private companion object {
        const val TAG = "CultureApplication"
    }
}

