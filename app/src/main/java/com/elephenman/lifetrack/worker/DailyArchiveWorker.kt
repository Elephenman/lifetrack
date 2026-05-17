package com.elephenman.lifetrack.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.elephenman.lifetrack.engine.DailySummaryComputer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.*

@HiltWorker
class DailyArchiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dailySummaryComputer: DailySummaryComputer
) : CoroutineWorker(appContext, workerParams) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override suspend fun doWork(): Result {
        return try {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = dateFormat.format(calendar.time)

            dailySummaryComputer.computeAndSaveDailySummary(yesterdayStr)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}