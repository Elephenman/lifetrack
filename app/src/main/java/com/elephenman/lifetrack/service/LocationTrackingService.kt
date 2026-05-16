package com.elephenman.lifetrack.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.elephenman.lifetrack.R
import com.elephenman.lifetrack.data.entity.LocationPoint
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.ui.home.MainActivity
import com.elephenman.lifetrack.util.PreferenceManager
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 核心定位服务 - 前台Service，开机自启，全天候GPS追踪
 *
 * 省电策略：
 * 1. 静止时：60s采样间隔
 * 2. 步行时：10s采样间隔
 * 3. 高速移动时(>30km/h)：5s采样间隔
 * 4. 精度>100m的点直接丢弃
 */
@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var repository: LocationRepository
    @Inject lateinit var prefs: PreferenceManager

    private val NOTIFICATION_CHANNEL_ID = "lifetrack_location"
    private val NOTIFICATION_ID = 10001

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private var lastLocation: Location? = null
    private var lastMotionState: MotionState = MotionState.STATIONARY
    private var currentAccuracyFilter: Float = 100f  // 精度阈值(m)

    private enum class MotionState {
        STATIONARY, WALKING, VEHICLE
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking()
            ACTION_TOGGLE -> {
                if (_isTracking.value) stopTracking() else startTracking()
            }
        }

        return START_STICKY  // 被杀后自动重启
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (_isTracking.value) return

        // 启动前台服务
        val notification = buildNotification("正在记录轨迹...")
        if (Build.VERSION.SDK_INT >= Build.VERSION.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 获取WakeLock（部分唤醒，保持CPU运行）
        acquireWakeLock()

        // 开始定位请求
        val request = buildLocationRequest()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)

        _isTracking.value = true
    }

    private fun stopTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        _isTracking.value = false
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    processLocation(location)
                }
            }
        }
    }

    private fun processLocation(location: Location) {
        // 精度过滤
        if (location.accuracy > currentAccuracyFilter) return

        // 运动状态推断 → 自适应采样频率
        inferMotionState(location)

        // 存入数据库
        lifecycleScope.launch(Dispatchers.IO) {
            val point = LocationPoint(
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speed = if (location.hasSpeed()) location.speed else null,
                provider = location.provider ?: "unknown",
                batteryPct = getBatteryPercentage()
            )
            repository.insertLocationPoint(point)

            // 更新通知
            withContext(Dispatchers.Main) {
                updateNotification(
                    "${location.latitude.format(4)}, ${location.longitude.format(4)} | " +
                    "${if (location.hasAccuracy()) "±${location.accuracy.toInt()}m" else ""}"
                )
            }
        }

        lastLocation = location
    }

    /**
     * 根据速度推断运动状态，动态调整采样频率
     */
    @SuppressLint("MissingPermission")
    private fun inferMotionState(location: Location) {
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0

        val newState = when {
            speedKmh < 2 -> MotionState.STATIONARY
            speedKmh < 8 -> MotionState.WALKING
            else -> MotionState.VEHICLE
        }

        if (newState != lastMotionState) {
            lastMotionState = newState
            // 重建定位请求以调整间隔
            fusedLocationClient.removeLocationUpdates(locationCallback)
            val request = buildLocationRequest()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        }
    }

    private fun buildLocationRequest(): LocationRequest {
        val intervalMs = when (lastMotionState) {
            MotionState.STATIONARY -> prefs.stationaryIntervalMs   // 默认60000ms
            MotionState.WALKING -> prefs.walkingIntervalMs         // 默认10000ms
            MotionState.VEHICLE -> prefs.vehicleIntervalMs         // 默认5000ms
        }

        return LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs)
            .setMinUpdateIntervalMillis(intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
    }

    // --- WakeLock ---

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "lifetrack::location-tracking"
        ).apply { acquire() }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    // --- 通知 ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续记录您的位置轨迹"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationTrackingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("足迹日记")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_location_dot)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "停止记录", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    // --- Utils ---

    private fun getBatteryPercentage(): Int {
        // 简化：实际需要注册BatteryChanged receiver
        return -1
    }

    private fun Double.format(digits: Int) = String.format("%.${digits}f", this)

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
    }

    companion object {
        const val ACTION_START = "com.elephenman.lifetrack.ACTION_START"
        const val ACTION_STOP = "com.elephenman.lifetrack.ACTION_STOP"
        const val ACTION_TOGGLE = "com.elephenman.lifetrack.ACTION_TOGGLE"
    }
}
