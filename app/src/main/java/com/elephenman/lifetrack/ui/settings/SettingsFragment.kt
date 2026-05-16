package com.elephenman.lifetrack.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.elephenman.lifetrack.R
import com.elephenman.lifetrack.data.migration.DataMigrationManager
import com.elephenman.lifetrack.service.LocationTrackingService

class SettingsFragment : PreferenceFragmentCompat() {

    private val migrationViewModel: DataMigrationViewModel by viewModels()

    companion object {
        private const val REQUEST_MANAGE_STORAGE = 1001
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        // 自启动开关
        findPreference<SwitchPreferenceCompat>("auto_start_on_boot")?.setOnPreferenceChangeListener { _, newValue ->
            if (newValue as Boolean) {
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

        // 备份数据
        findPreference<Preference>("data_backup")?.setOnPreferenceClickListener {
            if (checkStoragePermission()) {
                showBackupConfirmDialog()
            }
            true
        }

        // 恢复数据
        findPreference<Preference>("data_restore")?.setOnPreferenceClickListener {
            if (checkStoragePermission()) {
                showRestoreConfirmDialog()
            }
            true
        }

        // 数据导出（原有功能）
        findPreference<Preference>("data_export")?.setOnPreferenceClickListener {
            Toast.makeText(requireContext(), "功能开发中...", Toast.LENGTH_SHORT).show()
            true
        }

        // 观察迁移状态
        observeMigrationState()
        updateBackupInfo()
    }

    override fun onResume() {
        super.onResume()
        migrationViewModel.refreshBackupInfo()
        updateBackupInfo()
    }

    /**
     * 检查存储权限，Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
     */
    private fun checkStoragePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showStoragePermissionDialog()
                return false
            }
        }
        return true
    }

    private fun showStoragePermissionDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("需要文件访问权限")
            .setMessage("备份数据到 /sdcard/LifeTrack/ 文件夹需要「所有文件访问」权限。\n\n" +
                    "请在系统设置中为足迹日记开启此权限。")
            .setPositiveButton("去设置") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${requireContext().packageName}")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun observeMigrationState() {
        migrationViewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                DataMigrationViewModel.MigrationState.EXPORTING -> {
                    findPreference<Preference>("data_backup")?.isEnabled = false
                    findPreference<Preference>("data_restore")?.isEnabled = false
                }
                DataMigrationViewModel.MigrationState.IMPORTING -> {
                    findPreference<Preference>("data_backup")?.isEnabled = false
                    findPreference<Preference>("data_restore")?.isEnabled = false
                }
                DataMigrationViewModel.MigrationState.SUCCESS -> {
                    findPreference<Preference>("data_backup")?.isEnabled = true
                    findPreference<Preference>("data_restore")?.isEnabled = true
                    migrationViewModel.message.value?.let {
                        if (it.contains("恢复成功")) {
                            showRestartDialog(it)
                        } else {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            updateBackupInfo()
                        }
                    }
                    migrationViewModel.resetState()
                }
                DataMigrationViewModel.MigrationState.ERROR -> {
                    findPreference<Preference>("data_backup")?.isEnabled = true
                    findPreference<Preference>("data_restore")?.isEnabled = true
                    migrationViewModel.message.value?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    }
                    migrationViewModel.resetState()
                }
                else -> {
                    findPreference<Preference>("data_backup")?.isEnabled = true
                    findPreference<Preference>("data_restore")?.isEnabled = true
                }
            }
        }

        migrationViewModel.backupInfo.observe(viewLifecycleOwner) {
            updateBackupInfo()
        }
    }

    private fun updateBackupInfo() {
        val info = migrationViewModel.backupInfo.value
        val restorePref = findPreference<Preference>("data_restore")
        if (info != null && info.exists) {
            val timeStr = info.backupTime ?: "未知时间"
            val deviceStr = info.deviceName ?: "未知设备"
            val sizeStr = if (info.dbSizeKB > 1024) {
                String.format("%.1fMB", info.dbSizeKB / 1024.0)
            } else {
                "${info.dbSizeKB}KB"
            }
            restorePref?.summary = "上次备份：$timeStr\n$deviceStr | $sizeStr\n点击恢复此备份数据"
        } else {
            restorePref?.summary = "未找到备份数据\n请先将 /sdcard/LifeTrack/ 文件夹复制到手机"
        }
    }

    private fun showBackupConfirmDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("备份数据")
            .setMessage("将所有数据备份到 /sdcard/LifeTrack/ 文件夹？\n\n" +
                    "备份包含：\n" +
                    "• GPS轨迹和停留点\n" +
                    "• 每日汇总统计\n" +
                    "• 应用设置偏好\n\n" +
                    "换手机时，只需复制此文件夹到新手机即可恢复数据。")
            .setPositiveButton("备份") { _, _ ->
                migrationViewModel.exportData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRestoreConfirmDialog() {
        val info = migrationViewModel.backupInfo.value
        if (info == null || !info.exists) {
            Toast.makeText(requireContext(), "未找到备份数据", Toast.LENGTH_SHORT).show()
            return
        }

        val timeStr = info.backupTime ?: "未知"
        val deviceStr = info.deviceName ?: "未知"
        val sizeStr = if (info.dbSizeKB > 1024) {
            String.format("%.1fMB", info.dbSizeKB / 1024.0)
        } else {
            "${info.dbSizeKB}KB"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("恢复数据")
            .setMessage("确认从备份恢复数据？\n\n" +
                    "备份信息：\n" +
                    "• 时间：$timeStr\n" +
                    "• 设备：$deviceStr\n" +
                    "• 大小：$sizeStr\n\n" +
                    "⚠️ 当前数据将被替换！恢复后需要重启应用。")
            .setPositiveButton("恢复") { _, _ ->
                migrationViewModel.importData()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRestartDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("恢复成功")
            .setMessage("$message\n\n需要重启应用以加载恢复的数据。")
            .setPositiveButton("立即重启") { _, _ ->
                restartApp()
            }
            .setNegativeButton("稍后重启", null)
            .setOnDismissListener {
                updateBackupInfo()
            }
            .show()
    }

    private fun restartApp() {
        val launchIntent = requireContext().packageManager.getLaunchIntentForPackage(requireContext().packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
        requireActivity().finish()
        // 强制退出进程以确保数据库完全重新初始化
        System.exit(0)
    }

    private fun showAutoStartGuide() {
        Toast.makeText(requireContext(), "请在系统设置中允许足迹日记自启动", Toast.LENGTH_LONG).show()
    }

    private fun openBatteryOptimizationSettings() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        }
    }
}
