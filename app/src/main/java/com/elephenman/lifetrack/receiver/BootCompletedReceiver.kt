package com.elephenman.lifetrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.elephenman.lifetrack.service.LocationTrackingService
import com.elephenman.lifetrack.util.PreferenceManager

/**
 * 开机自启动接收器
 * 监听 BOOT_COMPLETED 广播，自动启动定位服务
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == null) return

        val validActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (intent.action !in validActions) return

        // 检查用户是否开启了开机自启
        val prefs = PreferenceManager(context)
        if (!prefs.autoStartOnBoot) return

        // 启动定位Service
        val serviceIntent = Intent(context, LocationTrackingService::class.java).apply {
            action = LocationTrackingService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
