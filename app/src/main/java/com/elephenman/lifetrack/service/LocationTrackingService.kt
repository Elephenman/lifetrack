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
import com.elephenman.lifetrack.R
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.entity.LocationPoint
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.ui.home.MainActivity
import com.elephenman.lifetrack.util.PreferenceManager
import com.google.android.gms.location.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class LocationTrackingService : LifecycleService() {

    @Inject lateinit var repository: LocationRepository
    @Inject lateinit var prefs: PreferenceManager
    @Inject lateinit var placeNameResolver: com.elephenman.lifetrack.util.PlaceNameResolver

    private val NOTIFICATION_CHANNEL_ID = "lifetrack_location"
    private val NOTIFICATION_ID = 10001

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var wakeLock: PowerManager.WakeLock? = null

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private var lastMotionState: MotionState = MotionState.STATIONARY
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // 实时停留检测状态
    private var stayStart: Long = 0L
    private var stayLat: Double = 0.0
    private var stayLng: Double = 0.0
    private var stayCount: Int = 0
    private var stayMaxDist: Double = 0.0
    private var lastSavedStayDate: String = ""
    private var lastSavedStayStart: Long = 0L
    private var cachedPlaceName: String? = null
    // 移动中：保存起点的单条LocationPoint，用于行程段距离计算
    private var tripStartSaved: Boolean = false

    private enum class MotionState { STATIONARY, WALKING, VEHICLE }

    companion object {
        const val ACTION_START = "com.elephenman.lifetrack.ACTION_START"
        const val ACTION_STOP = "com.elephenman.lifetrack.ACTION_STOP"
        const val ACTION_TOGGLE = "com.elephenman.lifetrack.ACTION_TOGGLE"

        private val _stayInfoFlow = MutableStateFlow<StayInfo?>(null)
        val stayInfoFlow: StateFlow<StayInfo?> = _stayInfoFlow
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationCallback()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // null intent = 系统因START_STICKY重启服务 → 自动恢复追踪
        if (intent == null || intent.action == null) {
            startTracking()
        } else {
            when (intent.action) {
                ACTION_START -> startTracking()
                ACTION_STOP -> stopTracking()
                ACTION_TOGGLE -> { if (_isTracking.value) stopTracking() else startTracking() }
            }
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (_isTracking.value) return
        val notification = buildNotification("正在记录轨迹...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        fusedLocationClient.requestLocationUpdates(buildLocationRequest(), locationCallback, mainLooper)
        _isTracking.value = true
    }

    private fun stopTracking() {
        finishCurrentStay()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        _isTracking.value = false
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { processLocation(it) }
            }
        }
    }

    private fun processLocation(location: Location) {
        val ok = !location.hasAccuracy() || location.accuracy <= 200f
        val low = location.hasAccuracy() && location.accuracy > 200f && location.accuracy <= 500f
        if (!ok && !low) return

        if (ok) inferMotionState(location)

        // 实时停留检测（仅高精度点）
        if (ok) detectStay(location)

        // 通知更新
        updateStayNotification(location)
    }

    /**
     * 停留检测核心逻辑：
     * - GPS持续采集，但位置不变时不写入数据库
     * - 50m内视为同一地点，仅更新实时状态
     * - 离开停留点时才保存StayPoint（enterTime + exitTime）
     * - 移动中保存行程起点/终点的LocationPoint用于距离计算
     */
    private fun detectStay(location: Location) {
        val now = location.time

        if (stayStart == 0L) {
            stayStart = now
            stayLat = location.latitude
            stayLng = location.longitude
            stayCount = 1
            stayMaxDist = 0.0
            tripStartSaved = false
            cachedPlaceName = null
            saveLocationPoint(location)
            tripStartSaved = true
            _stayInfoFlow.value = StayInfo(location.latitude, location.longitude, now, false)
            // 异步解析地名
            resolvePlaceName(location.latitude, location.longitude)
            return
        }

        val dist = haversine(stayLat, stayLng, location.latitude, location.longitude)

        if (dist <= 100.0) {
            stayCount++
            stayMaxDist = maxOf(stayMaxDist, dist)
            stayLat = (stayLat * (stayCount - 1) + location.latitude) / stayCount
            stayLng = (stayLng * (stayCount - 1) + location.longitude) / stayCount
            val staying = (now - stayStart) >= 10 * 60 * 1000L
            _stayInfoFlow.value = StayInfo(stayLat, stayLng, stayStart, staying, cachedPlaceName)
        } else {
            finishCurrentStay(now)
            saveLocationPoint(location)
            stayStart = now
            stayLat = location.latitude
            stayLng = location.longitude
            stayCount = 1
            stayMaxDist = 0.0
            cachedPlaceName = null
            _stayInfoFlow.value = StayInfo(location.latitude, location.longitude, now, false)
            resolvePlaceName(location.latitude, location.longitude)
        }
    }

    /** 结算当前停留：只在停留>5min时保存StayPoint */
    private fun finishCurrentStay(exitTime: Long = System.currentTimeMillis()) {
        if (stayStart == 0L) return
        val duration = exitTime - stayStart
        if (duration < 10 * 60 * 1000L) return

        val dateStr = dateFormat.format(Date(stayStart))
        if (dateStr == lastSavedStayDate && stayStart == lastSavedStayStart) return

        serviceScope.launch(Dispatchers.IO) {
            val name = cachedPlaceName ?: placeNameResolver.resolve(stayLat, stayLng)
            val stay = StayPoint(
                date = dateStr,
                enterTime = stayStart,
                exitTime = exitTime,
                latCenter = stayLat,
                lngCenter = stayLng,
                radius = stayMaxDist.toFloat().coerceIn(10f, 200f),
                poiName = name
            )
            repository.insertStayPoint(stay)
            lastSavedStayDate = dateStr
            lastSavedStayStart = stayStart

            // 生成行程段
            val saved = repository.getStayPointsByDate(dateStr)
            if (saved.size >= 2) {
                val from = saved[saved.size - 2]
                val to = saved[saved.size - 1]
                val trips = repository.getTripSegmentsByDate(dateStr)
                if (trips.none { it.fromStayId == from.id && it.toStayId == to.id }) {
                    val pts = repository.getLocationPoints(from.exitTime, to.enterTime)
                    val dist = if (pts.size >= 2) tripDistance(pts) else haversine(from.latCenter, from.lngCenter, to.latCenter, to.lngCenter)
                    val durMs = to.enterTime - from.exitTime
                    val spd = if (durMs > 0) (dist / durMs / 1000.0).toFloat() else 0f
                    repository.insertTripSegment(TripSegment(
                        date = dateStr, startTime = from.exitTime, endTime = to.enterTime,
                        fromStayId = from.id, toStayId = to.id,
                        distanceM = dist.toFloat(), transportMode = mode(spd), avgSpeed = spd
                    ))
                }
            }
        }
    }

    private fun resolvePlaceName(lat: Double, lng: Double) {
        serviceScope.launch(Dispatchers.IO) {
            val name = placeNameResolver.resolve(lat, lng)
            if (name != null) {
                cachedPlaceName = name
                val current = _stayInfoFlow.value
                if (current != null) {
                    _stayInfoFlow.value = current.copy(placeName = name)
                }
            }
        }
    }

    /** 仅在地点变化时保存单条LocationPoint（行程段距离计算需要） */
    private fun saveLocationPoint(location: Location) {
        serviceScope.launch(Dispatchers.IO) {
            repository.insertLocationPoint(LocationPoint(
                timestamp = location.time,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                accuracy = if (location.hasAccuracy()) location.accuracy else null,
                speed = if (location.hasSpeed()) location.speed else null,
                provider = location.provider ?: "unknown",
                batteryPct = -1
            ))
        }
    }

    private fun updateStayNotification(location: Location) {
        val acc = if (location.hasAccuracy()) "±${location.accuracy.toInt()}m" else ""
        val stay = _stayInfoFlow.value
        val stayStr = if (stay?.isStaying == true) {
            val sec = (System.currentTimeMillis() - stay.enterTimeMs) / 1000
            val m = sec / 60; val s = sec % 60
            " | 停留${m}m${s}s"
        } else ""
        updateNotification("${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)} | $acc$stayStr")
    }

    @SuppressLint("MissingPermission")
    private fun inferMotionState(location: Location) {
        val kmh = if (location.hasSpeed()) location.speed * 3.6 else 0.0
        val new = when { kmh < 2 -> MotionState.STATIONARY; kmh < 8 -> MotionState.WALKING; else -> MotionState.VEHICLE }
        if (new != lastMotionState) {
            lastMotionState = new
            fusedLocationClient.removeLocationUpdates(locationCallback)
            fusedLocationClient.requestLocationUpdates(buildLocationRequest(), locationCallback, mainLooper)
        }
    }

    private fun buildLocationRequest(): LocationRequest {
        val ms = when (lastMotionState) {
            MotionState.STATIONARY -> 60_000L
            MotionState.WALKING -> 20_000L
            MotionState.VEHICLE -> 10_000L
        }
        return LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ms)
            .setMinUpdateIntervalMillis(ms / 2)
            .setWaitForAccurateLocation(false)
            .build()
    }

    private fun mode(spd: Float): String {
        val k = spd * 3.6
        return when { k < 2 -> "stationary"; k < 8 -> "walk"; k < 25 -> "bike"; k < 120 -> "car"; else -> "train" }
    }

    private fun tripDistance(pts: List<LocationPoint>): Double {
        if (pts.size < 2) return 0.0
        var t = 0.0
        for (i in 1 until pts.size) t += haversine(pts[i-1].latitude, pts[i-1].longitude, pts[i].latitude, pts[i].longitude)
        return t
    }

    private fun haversine(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val r = 6371000.0
        val dLa = Math.toRadians(la2 - la1); val dLn = Math.toRadians(ln2 - ln1)
        val a = Math.sin(dLa/2).let{it*it} + Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLn/2).let{it*it}
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }

    // --- WakeLock ---
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "lifetrack::tracking").apply { acquire() }
    }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }

    // --- Notification ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(NOTIFICATION_CHANNEL_ID, "轨迹记录", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "持续记录您的位置轨迹"; setShowBadge(false); lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                })
        }
    }
    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val si = PendingIntent.getService(this, 1,
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("足迹日记").setContentText(text).setSmallIcon(R.drawable.ic_location_dot)
            .setContentIntent(pi).addAction(R.drawable.ic_stop, "停止记录", si).setOngoing(true).setSilent(true).build()
    }
    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun Double.f4() = String.format("%.4f", this)

    override fun onBind(intent: Intent): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        finishCurrentStay()
        releaseWakeLock()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        _isTracking.value = false
    }
}