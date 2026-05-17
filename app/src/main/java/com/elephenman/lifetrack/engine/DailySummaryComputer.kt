package com.elephenman.lifetrack.engine

import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.entity.StayPoint
import com.elephenman.lifetrack.data.entity.TripSegment
import com.elephenman.lifetrack.data.repository.LocationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DailySummaryComputer @Inject constructor(
    private val repository: LocationRepository,
    private val stayPointDetector: StayPointDetector
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun computeAndSaveDailySummary(dateStr: String): DailySummary? =
        withContext(Dispatchers.Default) {
            val (dayStart, dayEnd) = getDayTimeRange(dateStr)
            val points = repository.getLocationPoints(dayStart, dayEnd)
            if (points.isEmpty()) return@withContext null

            val existingStayPoints = repository.getStayPointsByDate(dateStr)
            val existingTripSegments = repository.getTripSegmentsByDate(dateStr)

            val stayPoints: List<StayPoint>
            val tripSegments: List<TripSegment>

            if (existingStayPoints.isEmpty()) {
                stayPoints = stayPointDetector.detectStayPoints(points, dateStr)
                stayPoints.forEach { repository.insertStayPoint(it) }
                val savedStayPoints = repository.getStayPointsByDate(dateStr)
                tripSegments = if (savedStayPoints.size >= 2) {
                    stayPointDetector.generateTripSegments(savedStayPoints, points, dateStr)
                } else emptyList()
                tripSegments.forEach { repository.insertTripSegment(it) }
            } else if (existingTripSegments.isEmpty() && existingStayPoints.size >= 2) {
                tripSegments = stayPointDetector.generateTripSegments(existingStayPoints, points, dateStr)
                tripSegments.forEach { repository.insertTripSegment(it) }
                stayPoints = existingStayPoints
            } else {
                stayPoints = existingStayPoints
                tripSegments = existingTripSegments
            }

            val totalDistance = tripSegments.sumOf {
                val d = it.distanceM ?: 0f
                if (d > 0f) d.toDouble() else {
                    // 没有距离数据时用两个停留点的haversine距离
                    val from = stayPoints.find { s -> s.id == it.fromStayId }
                    val to = stayPoints.find { s -> s.id == it.toStayId }
                    if (from != null && to != null) haversine(from.latCenter, from.lngCenter, to.latCenter, to.lngCenter)
                    else 0.0
                }
            }.toFloat()
            val firstMove = points.firstOrNull()?.timestamp
            val lastMove = points.lastOrNull()?.timestamp
            val outdoorMinutes = if (firstMove != null && lastMove != null) {
                ((lastMove - firstMove) / 60000).toInt()
            } else 0

            val summary = DailySummary(
                date = dateStr,
                totalDistance = totalDistance,
                totalOutdoorMin = outdoorMinutes,
                stayCount = stayPoints.size,
                firstMoveTime = firstMove,
                lastMoveTime = lastMove
            )
            repository.insertDailySummary(summary)
            return@withContext summary
        }

    private fun getDayTimeRange(dateStr: String): Pair<Long, Long> {
        val date = dateFormat.parse(dateStr)!!
        val cal = Calendar.getInstance().apply { time = date }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val dayEnd = cal.timeInMillis

        return Pair(dayStart, dayEnd)
    }

    private fun haversine(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val r = 6371000.0
        val dLa = Math.toRadians(la2 - la1); val dLn = Math.toRadians(ln2 - ln1)
        val a = Math.sin(dLa/2).let{it*it} + Math.cos(Math.toRadians(la1))*Math.cos(Math.toRadians(la2))*Math.sin(dLn/2).let{it*it}
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a))
    }
}