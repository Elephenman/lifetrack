package com.elephenman.lifetrack

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.elephenman.lifetrack.worker.DailyArchiveWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class LifeTrackApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailyArchive()
    }

    private fun scheduleDailyArchive() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val archiveRequest = PeriodicWorkRequestBuilder<DailyArchiveWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(calculateDelayToNext3AM(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_archive",
            ExistingPeriodicWorkPolicy.KEEP,
            archiveRequest
        )
    }

    /**
     * 计算到下一个凌晨3点的延迟
     */
    private fun calculateDelayToNext3AM(): Long {
        val now = Calendar.getInstance()
        val next3AM = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return next3AM.timeInMillis - now.timeInMillis
    }
}
