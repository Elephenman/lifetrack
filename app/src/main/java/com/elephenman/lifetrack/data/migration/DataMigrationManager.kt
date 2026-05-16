package com.elephenman.lifetrack.data.migration

import android.content.Context
import android.os.Build
import android.os.Environment
import com.elephenman.lifetrack.data.LifeTrackDatabase
import com.elephenman.lifetrack.util.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据迁移管理器
 *
 * 备份目录结构：/sdcard/LifeTrack/
 *   ├── lifetrack_database        (Room数据库文件)
 *   ├── lifetrack_database-wal    (WAL日志，如有)
 *   ├── lifetrack_database-shm    (共享内存，如有)
 *   ├── preferences.json          (用户偏好设置)
 *   └── metadata.json             (备份元数据：版本、时间、设备)
 *
 * 换手机流程：
 * 1. 旧手机：设置 → 备份数据 → 将 /sdcard/LifeTrack/ 文件夹复制到电脑
 * 2. 新手机：安装足迹日记 → 将文件夹复制到 /sdcard/LifeTrack/ → 设置 → 恢复数据
 */
@Singleton
class DataMigrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) {

    companion object {
        const val BACKUP_DIR_NAME = "LifeTrack"
        const val DB_NAME = "lifetrack_database"
        const val PREFS_FILE = "preferences.json"
        const val METADATA_FILE = "metadata.json"
        const val METADATA_VERSION = 1

        /** 备份文件夹的完整路径 - 始终使用 /sdcard/LifeTrack/ */
        fun getBackupDir(context: Context): File {
            return File(Environment.getExternalStorageDirectory(), BACKUP_DIR_NAME)
        }

        /**
         * 检查是否有外部存储访问权限
         * Android 11+ 需要 MANAGE_EXTERNAL_STORAGE
         * Android 10 及以下需要 READ/WRITE_EXTERNAL_STORAGE
         */
        fun hasStoragePermission(): Boolean {
            return Environment.isExternalStorageManager()
        }
    }

    private val gson = Gson()

    /**
     * 备份数据到 /sdcard/LifeTrack/
     *
     * 流程：
     * 1. 关闭数据库（确保WAL刷盘）
     * 2. 复制数据库文件到备份目录
     * 3. 导出偏好设置为JSON
     * 4. 写入元数据
     */
    suspend fun exportToFolder(): MigrationResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDir(context)
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            // 1. 关闭数据库，确保所有WAL内容刷盘
            val db = LifeTrackDatabase.getInstance(context)
            db.close()
            // 重置单例以便后续重新打开
            LifeTrackDatabase.resetInstance()

            // 2. 复制数据库文件
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                return@withContext MigrationResult.Error("数据库文件不存在")
            }

            copyFile(dbFile, File(backupDir, DB_NAME))

            // 复制WAL和SHM文件（如果存在）
            val walFile = File(dbFile.parent, "$DB_NAME-wal")
            if (walFile.exists()) {
                copyFile(walFile, File(backupDir, "$DB_NAME-wal"))
            }
            val shmFile = File(dbFile.parent, "$DB_NAME-shm")
            if (shmFile.exists()) {
                copyFile(shmFile, File(backupDir, "$DB_NAME-shm"))
            }

            // 清理旧备份中的WAL/SHM（如果新备份没有这些文件）
            if (!walFile.exists()) File(backupDir, "$DB_NAME-wal").delete()
            if (!shmFile.exists()) File(backupDir, "$DB_NAME-shm").delete()

            // 3. 导出偏好设置
            exportPreferences(backupDir)

            // 4. 写入元数据
            writeMetadata(backupDir)

            MigrationResult.Success("备份完成，共 ${dbFile.length() / 1024}KB 数据")
        } catch (e: Exception) {
            MigrationResult.Error("备份失败：${e.message}")
        }
    }

    /**
     * 从 /sdcard/LifeTrack/ 恢复数据
     *
     * 流程：
     * 1. 检查备份目录和文件是否存在
     * 2. 验证元数据版本兼容性
     * 3. 关闭当前数据库
     * 4. 复制备份的数据库文件到应用数据目录
     * 5. 导入偏好设置
     * 6. 重新打开数据库
     */
    suspend fun importFromFolder(): MigrationResult = withContext(Dispatchers.IO) {
        try {
            val backupDir = getBackupDir(context)

            // 1. 检查备份文件
            val backupDb = File(backupDir, DB_NAME)
            if (!backupDb.exists()) {
                return@withContext MigrationResult.Error("未找到备份数据，请确认 /sdcard/LifeTrack/ 文件夹已正确复制到手机")
            }

            // 2. 验证元数据
            val metadata = readMetadata(backupDir)
            if (metadata != null && metadata.version > METADATA_VERSION) {
                return@withContext MigrationResult.Error("备份版本(v${metadata.version})高于当前应用支持的版本(v$METADATA_VERSION)，请先更新应用")
            }

            // 3. 关闭当前数据库
            val db = LifeTrackDatabase.getInstance(context)
            db.close()
            LifeTrackDatabase.resetInstance()

            // 4. 复制备份数据库到应用目录
            val targetDb = context.getDatabasePath(DB_NAME)
            copyFile(backupDb, targetDb)

            // 复制WAL/SHM
            val backupWal = File(backupDir, "$DB_NAME-wal")
            val targetWal = File(targetDb.parent, "$DB_NAME-wal")
            if (backupWal.exists()) {
                copyFile(backupWal, targetWal)
            } else {
                targetWal.delete()
            }

            val backupShm = File(backupDir, "$DB_NAME-shm")
            val targetShm = File(targetDb.parent, "$DB_NAME-shm")
            if (backupShm.exists()) {
                copyFile(backupShm, targetShm)
            } else {
                targetShm.delete()
            }

            // 5. 导入偏好设置
            importPreferences(backupDir)

            // 6. 验证数据库可正常打开
            val newDb = LifeTrackDatabase.getInstance(context)
            newDb.openHelper.writableDatabase  // 触发打开验证
            newDb.close()
            LifeTrackDatabase.resetInstance()

            MigrationResult.Success("恢复成功，请重启应用以加载所有数据")
        } catch (e: Exception) {
            MigrationResult.Error("恢复失败：${e.message}")
        }
    }

    /**
     * 检查是否存在有效备份
     */
    fun hasBackup(): BackupInfo? {
        val backupDir = getBackupDir(context)
        val backupDb = File(backupDir, DB_NAME)
        if (!backupDb.exists()) return null

        val metadata = readMetadata(backupDir)
        return BackupInfo(
            exists = true,
            backupTime = metadata?.backupTime,
            deviceName = metadata?.deviceName,
            dbSizeKB = backupDb.length() / 1024,
            version = metadata?.version ?: 0
        )
    }

    // --- 偏好设置导出/导入 ---

    private fun exportPreferences(backupDir: File) {
        val prefs = context.getSharedPreferences("lifetrack_prefs", Context.MODE_PRIVATE)
        val allEntries = prefs.all

        // 同时导出 PreferenceManager 中定义的所有键
        val prefMap = mutableMapOf<String, Any?>()
        for ((key, value) in allEntries) {
            prefMap[key] = value
        }

        // 导出 preference-ktx 相关的设置
        val prefsKtx = context.getSharedPreferences("com.elephenman.lifetrack_preferences", Context.MODE_PRIVATE)
        for ((key, value) in prefsKtx.all) {
            prefMap["pref__$key"] = value
        }

        val json = gson.toJson(prefMap)
        File(backupDir, PREFS_FILE).writeText(json)
    }

    private fun importPreferences(backupDir: File) {
        val prefsFile = File(backupDir, PREFS_FILE)
        if (!prefsFile.exists()) return

        val json = prefsFile.readText()
        val type = object : TypeToken<Map<String, Any?>>() {}.type
        val prefMap: Map<String, Any?> = gson.fromJson(json, type)

        // 恢复 lifetrack_prefs
        val prefs = context.getSharedPreferences("lifetrack_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        val editor = prefs.edit()
        for ((key, value) in prefMap) {
            if (key.startsWith("pref__")) {
                continue  // 跳过 preference-ktx 的键，后面单独处理
            }
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
            }
        }
        editor.apply()

        // 恢复 preference-ktx 的设置
        val prefsKtx = context.getSharedPreferences("com.elephenman.lifetrack_preferences", Context.MODE_PRIVATE)
        val ktxEditor = prefsKtx.edit()
        for ((key, value) in prefMap) {
            if (!key.startsWith("pref__")) continue
            val realKey = key.removePrefix("pref__")
            when (value) {
                is String -> ktxEditor.putString(realKey, value)
                is Int -> ktxEditor.putInt(realKey, value)
                is Long -> ktxEditor.putLong(realKey, value)
                is Float -> ktxEditor.putFloat(realKey, value)
                is Boolean -> ktxEditor.putBoolean(realKey, value)
            }
        }
        ktxEditor.apply()
    }

    // --- 元数据 ---

    private fun writeMetadata(backupDir: File) {
        val metadata = Metadata(
            version = METADATA_VERSION,
            backupTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINESE).format(Date()),
            deviceName = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            appVersion = getAppVersion()
        )
        val json = gson.toJson(metadata)
        File(backupDir, METADATA_FILE).writeText(json)
    }

    private fun readMetadata(backupDir: File): Metadata? {
        val file = File(backupDir, METADATA_FILE)
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), Metadata::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    // --- 文件操作 ---

    private fun copyFile(src: File, dst: File) {
        FileInputStream(src).use { input ->
            FileOutputStream(dst).use { output ->
                val buffer = ByteArray(8192)
                var len: Int
                while (input.read(buffer).also { len = it } > 0) {
                    output.write(buffer, 0, len)
                }
                output.flush()
            }
        }
    }
}

// --- 数据类 ---

sealed class MigrationResult {
    data class Success(val message: String) : MigrationResult()
    data class Error(val message: String) : MigrationResult()
}

data class BackupInfo(
    val exists: Boolean,
    val backupTime: String? = null,
    val deviceName: String? = null,
    val dbSizeKB: Long = 0,
    val version: Int = 0
)

data class Metadata(
    val version: Int,
    val backupTime: String,
    val deviceName: String,
    val appVersion: String
)
