package com.elephenman.lifetrack.engine

import com.elephenman.lifetrack.data.entity.LocationPoint
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

/**
 * 停留点检测引擎
 *
 * 算法：基于时间-距离的停留点检测
 * - 同一位置50m半径内停留>5min → 标记为停留点
 * - 两个停留点之间 → 行程段
 * - 行程段速度推断交通模式
 */
@Singleton
class StayPointDetector @Inject constructor() {

    companion object {
        private const val DISTANCE_THRESHOLD_M = 50.0    // 停留距离阈值(m)
        private const val TIME_THRESHOLD_MS = 5 * 60 * 1000L  // 停留时间阈值(5min)
    }

    /**
     * 从GPS点序列中检测停留点
     */
    fun detectStayPoints(points: List<LocationPoint>, date: String): List<StayPoint> {
        if (points.isEmpty()) return emptyList()

        val stayPoints = mutableListOf<StayPoint>()
        var i = 0

        while (i < points.size) {
            var j = i + 1

            // 扩展窗口，直到点超出距离阈值
            while (j < points.size) {
                val dist = haversineDistance(
                    points[i].latitude, points[i].longitude,
                    points[j].latitude, points[j].longitude
                )
                if (dist > DISTANCE_THRESHOLD_M) break
                j++
            }

            // 检查是否满足时间阈值
            val duration = points[j - 1].timestamp - points[i].timestamp
            if (duration > TIME_THRESHOLD_MS) {
                val subset = points.subList(i, j)
                val (centerLat, centerLng) = calculateCenter(subset)
                val maxDist = subset.maxOf {
                    haversineDistance(centerLat, centerLng, it.latitude, it.longitude)
                }

                stayPoints.add(
                    StayPoint(
                        date = date,
                        enterTime = points[i].timestamp,
                        exitTime = points[j - 1].timestamp,
                        latCenter = centerLat,
                        lngCenter = centerLng,
                        radius = maxDist.toFloat().coerceIn(10f, 200f),
                        poiName = null,  // 后续由POI引擎填充
                        poiAddress = null
                    )
                )
                i = j  // 跳到窗口外
            } else {
                i++
            }
        }

        return stayPoints
    }

    /**
     * 从停留点生成行程段
     */
    fun generateTripSegments(
        stayPoints: List<StayPoint>,
        allPoints: List<LocationPoint>,
        date: String
    ): List<TripSegment> {
        if (stayPoints.size < 2) return emptyList()

        val segments = mutableListOf<TripSegment>()

        for (i in 0 until stayPoints.size - 1) {
            val from = stayPoints[i]
            val to = stayPoints[i + 1]

            // 计算行程距离
            val tripPoints = allPoints.filter {
                it.timestamp >= from.exitTime && it.timestamp <= to.enterTime
            }

            val distance = calculateTotalDistance(tripPoints)
            val durationMs = to.enterTime - from.exitTime
            val avgSpeed = if (durationMs > 0) (distance / (durationMs / 1000.0)).toFloat() else 0f

            val transportMode = inferTransportMode(avgSpeed)

            segments.add(
                TripSegment(
                    date = date,
                    startTime = from.exitTime,
                    endTime = to.enterTime,
                    fromStayId = from.id.let { if (it == 0L) null else it },
                    toStayId = to.id.let { if (it == 0L) null else it },
                    distanceM = distance.toFloat(),
                    transportMode = transportMode,
                    avgSpeed = avgSpeed
                )
            )
        }

        return segments
    }

    /**
     * 推断交通模式
     */
    private fun inferTransportMode(avgSpeedMs: Float): String {
        val speedKmh = avgSpeedMs * 3.6
        return when {
            speedKmh < 2 -> "stationary"
            speedKmh < 8 -> "walk"
            speedKmh < 25 -> "bike"
            speedKmh < 120 -> "car"
            else -> "train"
        }
    }

    // --- 数学工具 ---

    /**
     * Haversine公式计算两点间距离(m)
     */
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0  // 地球半径(m)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * 计算点集的质心
     */
    private fun calculateCenter(points: List<LocationPoint>): Pair<Double, Double> {
        val avgLat = points.map { it.latitude }.average()
        val avgLng = points.map { it.longitude }.average()
        return Pair(avgLat, avgLng)
    }

    /**
     * 计算轨迹总距离(m)
     */
    private fun calculateTotalDistance(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineDistance(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude
            )
        }
        return total
    }
}
