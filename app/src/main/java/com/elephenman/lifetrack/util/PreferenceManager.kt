package com.elephenman.lifetrack.util

import android.content.Context
import android.content.SharedPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户偏好设置管理
 */
@Singleton
class PreferenceManager @Inject constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lifetrack_prefs", Context.MODE_PRIVATE)

    // --- 采样间隔 ---

    var stationaryIntervalMs: Long
        get() = prefs.getLong("stationary_interval_ms", 60_000L)
        set(value) = prefs.edit().putLong("stationary_interval_ms", value).apply()

    var walkingIntervalMs: Long
        get() = prefs.getLong("walking_interval_ms", 10_000L)
        set(value) = prefs.edit().putLong("walking_interval_ms", value).apply()

    var vehicleIntervalMs: Long
        get() = prefs.getLong("vehicle_interval_ms", 5_000L)
        set(value) = prefs.edit().putLong("vehicle_interval_ms", value).apply()

    // --- 功能开关 ---

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean("auto_start_on_boot", true)
        set(value) = prefs.edit().putBoolean("auto_start_on_boot", value).apply()

    var stealthMode: Boolean
        get() = prefs.getBoolean("stealth_mode", false)
        set(value) = prefs.edit().putBoolean("stealth_mode", value).apply()

    var enableEncryption: Boolean
        get() = prefs.getBoolean("enable_encryption", false)
        set(value) = prefs.edit().putBoolean("enable_encryption", value).apply()

    // --- 数据管理 ---

    var dataRetentionDays: Int
        get() = prefs.getInt("data_retention_days", 365)
        set(value) = prefs.edit().putInt("data_retention_days", value).apply()

    var accuracyFilterM: Float
        get() = prefs.getFloat("accuracy_filter_m", 100f)
        set(value) = prefs.edit().putFloat("accuracy_filter_m", value).apply()

    // --- 离线地图 ---

    var offlineMapTileSource: String
        get() = prefs.getString("offline_map_tile_source", "Mapnik") ?: "Mapnik"
        set(value) = prefs.edit().putString("offline_map_tile_source", value).apply()

    var tileDownloadWifiOnly: Boolean
        get() = prefs.getBoolean("tile_download_wifi_only", true)
        set(value) = prefs.edit().putBoolean("tile_download_wifi_only", value).apply()

    // --- 逆地理编码 ---

    var gaodeApiKey: String?
        get() = prefs.getString("gaode_api_key", null)
        set(value) = prefs.edit().putString("gaode_api_key", value).apply()

    var tencentMapKey: String?
        get() = prefs.getString("tencent_map_key", null)
        set(value) = prefs.edit().putString("tencent_map_key", value).apply()
}
