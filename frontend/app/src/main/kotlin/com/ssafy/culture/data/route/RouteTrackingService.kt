package com.ssafy.culture.data.route

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ssafy.culture.MainActivity
import com.ssafy.culture.R
import com.ssafy.culture.domain.model.RoutePoint
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RouteTrackingService : Service() {

    @Inject
    lateinit var routeHistoryStore: RouteHistoryStore

    private val locationManager: LocationManager? by lazy {
        getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    }

    private var trackingStoryId: Long? = null
    private var lastSampledTimestamp: Long = 0L

    private val listener = object : LocationListener {
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val storyId: Long = intent?.getLongExtra(ExtraStoryId, -1L)?.takeIf { it > 0 } ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!canStartLocationTracking() || !startForegroundNotification()) {
            stopSelf()
            return START_NOT_STICKY
        }
        trackingStoryId = storyId
        routeHistoryStore.setActiveStoryId(storyId)
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        stopLocationUpdates()
        trackingStoryId = null
        super.onDestroy()
    }

    private fun startForegroundNotification(): Boolean {
        val notification: android.app.Notification = NotificationCompat.Builder(this, ChannelId)
            .setSmallIcon(R.drawable.ic_app_icon)
            .setContentTitle("미션 경로 기록 중")
            .setContentText("문화재 사이의 길을 따라 좌표를 기록하고 있어요.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(buildOpenAppIntent())
            .build()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NotificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } else {
                startForeground(NotificationId, notification)
            }
        }.isSuccess
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val manager: LocationManager = locationManager ?: return
        if (!LocationManagerCompat.isLocationEnabled(manager)) return
        if (!hasFineLocationPermission()) return
        lastSampledTimestamp = 0L
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            if (!manager.allProviders.contains(provider)) return@forEach
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    MinSampleIntervalMillis,
                    MinSampleDistanceMeters,
                    listener,
                )
            }
        }
    }

    private fun stopLocationUpdates() {
        val manager: LocationManager = locationManager ?: return
        runCatching { manager.removeUpdates(listener) }
    }

    private fun canStartLocationTracking(): Boolean {
        val manager: LocationManager = locationManager ?: return false
        if (!LocationManagerCompat.isLocationEnabled(manager)) return false
        return hasFineLocationPermission()
    }

    private fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = NotificationManagerCompat.from(this)
        if (manager.getNotificationChannel(ChannelId) != null) return
        val channel = NotificationChannel(
            ChannelId,
            "경로 기록",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "미션 동안 위치 좌표를 기록하는 동안 표시되는 알림이에요."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildOpenAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    companion object {
        const val ExtraStoryId: String = "extra_story_id"
        const val ChannelId: String = "route_tracking_channel"
        private const val NotificationId: Int = 7321
        private const val MinSampleIntervalMillis: Long = 60_000L
        private const val MinSampleDistanceMeters: Float = 5f

        fun start(context: Context, storyId: Long): Boolean {
            val intent = Intent(context, RouteTrackingService::class.java).apply {
                putExtra(ExtraStoryId, storyId)
            }
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.isSuccess
        }

        fun stop(context: Context) {
            val intent = Intent(context, RouteTrackingService::class.java)
            context.stopService(intent)
        }
    }
}
