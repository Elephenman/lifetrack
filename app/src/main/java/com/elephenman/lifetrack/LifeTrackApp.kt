package com.elephenman.lifetrack

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.elephenman.lifetrack.service.LocationTrackingService
import com.elephenman.lifetrack.worker.DailyArchiveWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.Calendar
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
        autoStartTrackingService()
    }

    /** 权限已授权时自动启动定位服务 */
    private fun autoStartTrackingService() {
        val hasFine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine) return  // 没权限就不启动，等用户授权

        val intent = Intent(this, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun scheduleDailyArchive() {
        val periodicRequest = PeriodicWorkRequestBuilder<DailyArchiveWorker>(
            24, TimeUnit.HOURS
        )
            .setInitialDelay(calculateDelayToNext3AM(), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_archive",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )

        val immediateRequest = OneTimeWorkRequestBuilder<DailyArchiveWorker>().build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "daily_archive_immediate",
            ExistingWorkPolicy.KEEP,
            immediateRequest
        )
    }

    private fun calculateDelayToNext3AM(): Long {
        val now = Calendar.getInstance()
        val next3AM = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 3)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next3AM.timeInMillis - now.timeInMillis
    }
}