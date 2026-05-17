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

            val totalDistance = tripSegments.sumOf { (it.distanceM ?: 0f).toDouble() }.toFloat()
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
}