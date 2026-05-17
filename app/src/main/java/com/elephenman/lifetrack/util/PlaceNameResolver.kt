package com.elephenman.lifetrack.util

import android.content.Context
import android.location.Geocoder
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaceNameResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: PreferenceManager
) {
    private val geocoder = Geocoder(context)
    private val cache = mutableMapOf<String, String?>()

    // 腾讯地图默认key（个人开发者免费额度，每日5000次）
    private val defaultTencentKey = "OB4BZ-D4W3U-B7VVO-4PJWW-6TKDJ-WPB77"

    suspend fun resolve(lat: Double, lng: Double): String? {
        val gridKey = "${(lat * 1000).toInt()}/${(lng * 1000).toInt()}"
        cache[gridKey]?.let { return it }

        // 1. 高德API（如果用户配置了key，国内最准）
        val gaodeKey = prefs.gaodeApiKey
        if (!gaodeKey.isNullOrBlank()) {
            val result = gaodeReverse(lat, lng, gaodeKey)
            if (result != null) { cache[gridKey] = result; return result }
        }

        // 2. 腾讯地图API（国内稳定，无需用户配置key）
        val tencentResult = tencentReverse(lat, lng)
        if (tencentResult != null) { cache[gridKey] = tencentResult; return tencentResult }

        // 3. 系统Geocoder
        val sys = systemGeocode(lat, lng)
        if (sys != null) { cache[gridKey] = sys; return sys }

        cache[gridKey] = null
        return null
    }

    private suspend fun systemGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (addresses.isNullOrEmpty()) return@withContext null
            val addr = addresses[0]
            addr.featureName?.takeIf { it.isNotBlank() && it.length > 1 && !it.matches(Regex("\\d+")) }
                ?: addr.thoroughfare?.takeIf { it.isNotBlank() }
                ?: addr.subLocality?.takeIf { it.isNotBlank() }
                ?: addr.locality?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    private suspend fun gaodeReverse(lat: Double, lng: Double, key: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://restapi.amap.com/v3/geocode/regeo?key=$key&location=$lng,$lat&extensions=base"
            val json = URL(url).readText()
            val obj = JSONObject(json)
            if (obj.optString("status") != "1") return@withContext null

            val regeo = obj.optJSONObject("regeocode") ?: return@withContext null
            val addr = regeo.optJSONObject("addressComponent") ?: return@withContext null

            val building = regeo.optJSONObject("building")?.optString("name")?.takeIf { it.isNotBlank() }
            if (!building.isNullOrEmpty()) return@withContext building

            val poi = regeo.optJSONArray("pois")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
            if (!poi.isNullOrEmpty()) return@withContext poi

            val township = addr.optString("township")?.takeIf { it.isNotBlank() }
            val district = addr.optString("district")?.takeIf { it.isNotBlank() }
            val city = addr.optString("city")?.takeIf { it.isNotBlank() }

            township ?: district ?: city
        } catch (_: Exception) { null }
    }

    private suspend fun tencentReverse(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val key = prefs.tencentMapKey ?: defaultTencentKey
            val url = URL("https://apis.map.qq.com/ws/geocoder/v1/?location=$lat,$lng&key=$key&get_poi=1&output=json")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val obj = JSONObject(json)
            if (obj.optInt("status") != 0) return@withContext null

            val result = obj.optJSONObject("result") ?: return@withContext null

            // 优先用 recommend 地名（如"海南大学(海甸校区)"）
            val formatted = result.optJSONObject("formatted_addresses")
            val recommend = formatted?.optString("recommend")?.takeIf { it.isNotBlank() }
            if (!recommend.isNullOrEmpty()) return@withContext recommend

            // POI列表中最近的地点
            val pois = result.optJSONArray("pois")
            if (pois != null && pois.length() > 0) {
                val poi = pois.optJSONObject(0)
                val poiName = poi?.optString("title")?.takeIf { it.isNotBlank() }
                if (!poiName.isNullOrEmpty()) return@withContext poiName
            }

            // 行政区划
            val addrComp = result.optJSONObject("address_component") ?: return@withContext null
            val street = addrComp.optString("street")?.takeIf { it.isNotBlank() }
            val district = addrComp.optString("district")?.takeIf { it.isNotBlank() }
            val city = addrComp.optString("city")?.takeIf { it.isNotBlank() }

            street ?: district ?: city
        } catch (e: Exception) {
            Log.w("PlaceName", "Tencent failed: ${e.message}")
            null
        }
    }
}