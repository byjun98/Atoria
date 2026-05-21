package com.ssafy.culture.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.appPreferences by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferenceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissionOnboardingCompleted: Flow<Boolean> =
        context.appPreferences.data.map { preferences ->
            preferences[PermissionOnboardingCompletedKey] ?: false
        }

    val darkModeEnabled: Flow<Boolean> =
        context.appPreferences.data.map { preferences ->
            preferences[DarkModeEnabledKey] ?: false
        }

    val hasSeenEbookHint: Flow<Boolean> =
        context.appPreferences.data.map { preferences ->
            preferences[HasSeenEbookHintKey] ?: false
        }

    val captureTimerEnabled: Flow<Boolean> =
        context.appPreferences.data.map { preferences ->
            preferences[CaptureTimerEnabledKey] ?: DefaultCaptureTimerEnabled
        }

    val captureTimerSeconds: Flow<Int> =
        context.appPreferences.data.map { preferences ->
            (preferences[CaptureTimerSecondsKey] ?: DefaultCaptureTimerSeconds)
                .coerceIn(MinCaptureTimerSeconds, MaxCaptureTimerSeconds)
        }

    val routeTrackingMode: Flow<RouteTrackingMode> =
        context.appPreferences.data.map { preferences ->
            RouteTrackingMode.fromKey(preferences[RouteTrackingModeKey])
        }

    val hasChosenRouteTrackingMode: Flow<Boolean> =
        context.appPreferences.data.map { preferences ->
            preferences[RouteTrackingModeKey] != null
        }

    suspend fun setPermissionOnboardingCompleted(isCompleted: Boolean) {
        context.appPreferences.edit { preferences ->
            preferences[PermissionOnboardingCompletedKey] = isCompleted
        }
    }

    suspend fun setDarkModeEnabled(isEnabled: Boolean) {
        context.appPreferences.edit { preferences ->
            preferences[DarkModeEnabledKey] = isEnabled
        }
    }

    suspend fun setHasSeenEbookHint(value: Boolean) {
        context.appPreferences.edit { preferences ->
            preferences[HasSeenEbookHintKey] = value
        }
    }

    suspend fun setCaptureTimerEnabled(isEnabled: Boolean) {
        context.appPreferences.edit { preferences ->
            preferences[CaptureTimerEnabledKey] = isEnabled
        }
    }

    suspend fun setCaptureTimerSeconds(seconds: Int) {
        val clamped: Int = seconds.coerceIn(MinCaptureTimerSeconds, MaxCaptureTimerSeconds)
        context.appPreferences.edit { preferences ->
            preferences[CaptureTimerSecondsKey] = clamped
        }
    }

    suspend fun setRouteTrackingMode(mode: RouteTrackingMode) {
        context.appPreferences.edit { preferences ->
            preferences[RouteTrackingModeKey] = mode.key
        }
    }

    suspend fun isEbookFairyTaleModeEnabled(ebookId: String): Boolean =
        context.appPreferences.data.first()[ebookFairyTaleModeKey(ebookId)] ?: false

    suspend fun setEbookFairyTaleModeEnabled(
        ebookId: String,
        isEnabled: Boolean,
    ) {
        context.appPreferences.edit { preferences ->
            preferences[ebookFairyTaleModeKey(ebookId)] = isEnabled
        }
    }

    companion object {
        const val MinCaptureTimerSeconds: Int = 1
        const val MaxCaptureTimerSeconds: Int = 10
        const val DefaultCaptureTimerSeconds: Int = 3
        const val DefaultCaptureTimerEnabled: Boolean = true
    }
}

private val PermissionOnboardingCompletedKey = booleanPreferencesKey("permission_onboarding_completed")
private val DarkModeEnabledKey = booleanPreferencesKey("dark_mode_enabled")
private val HasSeenEbookHintKey = booleanPreferencesKey("has_seen_ebook_hint")
private val CaptureTimerEnabledKey = booleanPreferencesKey("capture_timer_enabled")
private val CaptureTimerSecondsKey = intPreferencesKey("capture_timer_seconds")
private val RouteTrackingModeKey = stringPreferencesKey("route_tracking_mode")

private fun ebookFairyTaleModeKey(ebookId: String) =
    booleanPreferencesKey("ebook_fairy_tale_mode_${ebookId.toPreferenceKeySuffix()}")

private fun String.toPreferenceKeySuffix(): String =
    ifBlank { "latest" }.replace(Regex("[^A-Za-z0-9._-]"), "_")

enum class RouteTrackingMode(val key: String) {
    Accurate("accurate"),
    Simple("simple"),
    Off("off");

    companion object {
        val Default: RouteTrackingMode = Simple
        fun fromKey(key: String?): RouteTrackingMode =
            entries.firstOrNull { mode -> mode.key == key } ?: Default
    }
}
