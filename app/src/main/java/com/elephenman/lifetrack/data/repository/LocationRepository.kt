package com.elephenman.lifetrack.data.repository

import com.elephenman.lifetrack.data.dao.*
import com.elephenman.lifetrack.data.entity.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationPointDao: LocationPointDao,
    private val stayPointDao: StayPointDao,
    private val tripSegmentDao: TripSegmentDao,
    private val dailySummaryDao: DailySummaryDao
) {
    // LocationPoint
    suspend fun insertLocationPoint(point: LocationPoint) = locationPointDao.insert(point)
    suspend fun getLocationPoints(startTime: Long, endTime: Long) = locationPointDao.getByTimeRange(startTime, endTime)
    fun getLocationPointsFlow(startTime: Long, endTime: Long) = locationPointDao.getByTimeRangeFlow(startTime, endTime)
    suspend fun getRecentPoints(limit: Int = 100) = locationPointDao.getRecent(limit)
    suspend fun deletePointsBefore(timestamp: Long) = locationPointDao.deleteBefore(timestamp)
    suspend fun getLocationPointsByDate(dateStr: String): List<LocationPoint> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val start = fmt.parse(dateStr)!!.time
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = start }
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return locationPointDao.getByDateRange(start, end)
    }
    fun getLocationPointsByDateFlow(dateStr: String): kotlinx.coroutines.flow.Flow<List<LocationPoint>> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val start = fmt.parse(dateStr)!!.time
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = start }
        cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return locationPointDao.getByDateRangeFlow(start, end)
    }

    // StayPoint
    suspend fun insertStayPoint(point: StayPoint) = stayPointDao.insert(point)
    suspend fun getStayPointsByDate(date: String) = stayPointDao.getByDate(date)
    fun getStayPointsByDateFlow(date: String) = stayPointDao.getByDateFlow(date)
    suspend fun getTopPoi(sinceDate: String, limit: Int = 5) = stayPointDao.getTopPoi(sinceDate, limit)
    suspend fun updateStayPoint(point: StayPoint) = stayPointDao.update(point)
    suspend fun deleteStayPointsBefore(date: String) = stayPointDao.deleteBefore(date)

    // TripSegment
    suspend fun insertTripSegment(segment: TripSegment) = tripSegmentDao.insert(segment)
    suspend fun getTripSegmentsByDate(date: String) = tripSegmentDao.getByDate(date)
    fun getTripSegmentsByDateFlow(date: String) = tripSegmentDao.getByDateFlow(date)

    // DailySummary
    suspend fun insertDailySummary(summary: DailySummary) = dailySummaryDao.insert(summary)
    suspend fun getDailySummary(date: String) = dailySummaryDao.getByDate(date)
    fun getDailySummaryFlow(date: String) = dailySummaryDao.getByDateFlow(date)
    fun getAllDailySummariesFlow() = dailySummaryDao.getAllFlow()
    suspend fun getDailySummariesByDateRange(startDate: String, endDate: String) = dailySummaryDao.getByDateRange(startDate, endDate)
}
