package com.ssafy.culture.data.route

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ssafy.culture.data.preferences.AppPreferenceStore
import com.ssafy.culture.data.preferences.RouteTrackingMode
import com.ssafy.culture.di.ApplicationScope
import com.ssafy.culture.domain.model.RoutePoint
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Singleton
class RouteTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routeHistoryStore: RouteHistoryStore,
    private val appPreferenceStore: AppPreferenceStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var trackingStoryId: Long? = null

    @Volatile
    private var activeMode: RouteTrackingMode = RouteTrackingMode.Off
    private var lastSampledTimestamp: Long = 0L

    private val foregroundListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val storyId: Long = trackingStoryId ?: return
            val now: Long = System.currentTimeMillis()
            if (now - lastSampledTimestamp < MinSampleIntervalMillis) return
            lastSampledTimestamp = now
            routeHistoryStore.appendPoint(
                storyId = storyId,
                point = RoutePoint(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = location.time.takeIf { it > 0 } ?: now,
                ),
            )
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun start(storyId: Long) {
        applicationScope.launch {
            val mode: RouteTrackingMode = runCatching {
                appPreferenceStore.routeTrackingMode.first()
            }.getOrElse { RouteTrackingMode.Default }
            startWithMode(storyId = storyId, mode = mode)
        }
    }

    fun stop() {
        stopInternal()
    }

    fun refreshModeIfActive() {
        val activeStoryId: Long = trackingStoryId ?: return
        applicationScope.launch {
            val mode: RouteTrackingMode = runCatching {
                appPreferenceStore.routeTrackingMode.first()
            }.getOrElse { RouteTrackingMode.Default }
            if (mode != activeMode) {
                startWithMode(storyId = activeStoryId, mode = mode)
            }
        }
    }

    @Synchronized
    private fun startWithMode(storyId: Long, mode: RouteTrackingMode) {
        if (trackingStoryId == storyId && activeMode == mode) return
        stopInternal()
        val didStart: Boolean = when (mode) {
            RouteTrackingMode.Off -> false
            RouteTrackingMode.Simple -> startForegroundOnlyTracking()
            RouteTrackingMode.Accurate -> startBackgroundService(storyId)
        }
        if (!didStart) return
        trackingStoryId = storyId
        activeMode = mode
        routeHistoryStore.setActiveStoryId(storyId)
    }

    private fun startForegroundOnlyTracking(): Boolean {
        val manager: LocationManager = locationManager ?: return false
        if (!LocationManagerCompat.isLocationEnabled(manager)) return false
        if (!hasFineLocationPermission()) return false
        lastSampledTimestamp = 0L
        val didRequestGps: Boolean = requestForegroundUpdates(manager, LocationManager.GPS_PROVIDER)
        val didRequestNetwork: Boolean = requestForegroundUpdates(manager, LocationManager.NETWORK_PROVIDER)
        return didRequestGps || didRequestNetwork
    }

    private fun startBackgroundService(storyId: Long): Boolean {
        val manager: LocationManager = locationManager ?: return false
        if (!LocationManagerCompat.isLocationEnabled(manager)) return false
        if (!hasFineLocationPermission()) return false
        return RouteTrackingService.start(context, storyId)
    }

    @Synchronized
    private fun stopInternal() {
        val previousMode: RouteTrackingMode = activeMode
        if (previousMode == RouteTrackingMode.Accurate) {
            RouteTrackingService.stop(context)
        } else {
            val manager: LocationManager? = locationManager
            if (manager != null) {
                runCatching { manager.removeUpdates(foregroundListener) }
            }
        }
        trackingStoryId = null
        activeMode = RouteTrackingMode.Off
    }

    @SuppressLint("MissingPermission")
    private fun requestForegroundUpdates(manager: LocationManager, provider: String): Boolean {
        if (!manager.allProviders.contains(provider)) return false
        return runCatching {
            manager.requestLocationUpdates(
                provider,
                MinSampleIntervalMillis,
                MinSampleDistanceMeters,
                foregroundListener,
                Looper.getMainLooper(),
            )
        }.isSuccess
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val MinSampleIntervalMillis: Long = 60_000L
        const val MinSampleDistanceMeters: Float = 5f
    }
}
