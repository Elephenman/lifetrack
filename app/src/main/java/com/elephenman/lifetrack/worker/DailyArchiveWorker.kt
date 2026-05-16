package com.elephenman.lifetrack.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elephenman.lifetrack.data.entity.DailySummary
import com.elephenman.lifetrack.data.repository.LocationRepository
import com.elephenman.lifetrack.engine.StayPointDetector
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 每日归档 Worker
 *
 * 凌晨自动运行，处理前一天的数据：
 * 1. 从原始 GPS 点检测停留点
 * 2. 生成行程段
 * 3. 计算并保存每日汇总
 */
@HiltWorker
class DailyArchiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: LocationRepository,
    private val stayPointDetector: StayPointDetector
) : CoroutineWorker(appContext, workerParams) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        return try {
            // 处理昨天的数据
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(calendar.time)

            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val dayStart = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, 23)
            calendar.set(Calendar.MINUTE, 59)
            calendar.set(Calendar.SECOND, 59)
            calendar.set(Calendar.MILLISECOND, 999)
            val dayEnd = calendar.timeInMillis

            // 1. 获取当天所有 GPS 点
            val points = repository.getLocationPoints(dayStart, dayEnd)
            if (points.isEmpty()) {
                return Result.success()
            }

            // 2. 检测停留点
            val stayPoints = stayPointDetector.detectStayPoints(points, yesterdayStr)
            stayPoints.forEach { repository.insertStayPoint(it) }

            // 3. 生成行程段
            // 注意：刚插入的 StayPoint 的 id 是自增的，需要重新查询获取带 id 的版本
            val savedStayPoints = repository.getStayPointsByDate(yesterdayStr)
            val tripSegments = stayPointDetector.generateTripSegments(savedStayPoints, points, yesterdayStr)
            tripSegments.forEach { repository.insertTripSegment(it) }

            // 4. 计算每日汇总
            val totalDistance = tripSegments.sumOf { (it.distanceM ?: 0f).toDouble() }.toFloat()
            val outdoorMinutes = stayPoints.sumOf { (it.exitTime - it.enterTime) / 60000 }.toInt()
            val firstMove = points.firstOrNull()?.timestamp
            val lastMove = points.lastOrNull()?.timestamp

            val summary = DailySummary(
                date = yesterdayStr,
                totalDistance = totalDistance,
                totalOutdoorMin = outdoorMinutes,
                stayCount = stayPoints.size,
                firstMoveTime = firstMove,
                lastMoveTime = lastMove
            )
            repository.insertDailySummary(summary)

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
