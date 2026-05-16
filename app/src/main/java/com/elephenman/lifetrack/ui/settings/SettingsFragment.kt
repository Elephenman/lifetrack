package com.elephenman.lifetrack.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.elephenman.lifetrack.R
import com.elephenman.lifetrack.service.LocationTrackingService

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // 自启动开关
        findPreference<SwitchPreferenceCompat>("auto_start_on_boot")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                // 引导用户到系统自启动设置
                showAutoStartGuide()
            }
            true
        }

        // 隐身模式
        findPreference<SwitchPreferenceCompat>("stealth_mode")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
                Toast.makeText(requireContext(), "隐身模式已开启，定位暂停", Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_STOP
                }
                requireContext().startService(intent)
            } else {
                val intent = Intent(requireContext(), LocationTrackingService::class.java).apply {
                    action = LocationTrackingService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireContext().startForegroundService(intent)
                } else {
                    requireContext().startService(intent)
                }
                Toast.makeText(requireContext(), "隐身模式已关闭，恢复记录", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 电池优化白名单引导
        findPreference<Preference>("battery_optimization")?.setOnPreferenceClickListener {
            openBatteryOptimizationSettings()
            true
        }
    }

    private fun showAutoStartGuide() {
        Toast.makeText(requireContext(), "请在系统设置中允许足迹日记自启动", Toast.LENGTH_LONG).show()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            // fallback到应用详情页
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }
}
