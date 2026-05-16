package com.elephenman.lifetrack.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.elephenman.lifetrack.data.migration.BackupInfo
import com.elephenman.lifetrack.data.migration.DataMigrationManager
import com.elephenman.lifetrack.data.migration.MigrationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 数据迁移 ViewModel
 *
 * 管理备份/恢复的异步操作和状态暴露
 */
@HiltViewModel
class DataMigrationViewModel @Inject constructor(
    application: Application,
    private val migrationManager: DataMigrationManager
) : AndroidViewModel(application) {

    enum class MigrationState {
        IDLE,           // 空闲
        EXPORTING,      // 备份中
        IMPORTING,      // 恢复中
        SUCCESS,        // 操作成功
        ERROR           // 操作失败
    }

    private val _state = MutableLiveData(MigrationState.IDLE)
    val state: LiveData<MigrationState> = _state

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _backupInfo = MutableLiveData<BackupInfo?>()
    val backupInfo: LiveData<BackupInfo?> = _backupInfo

    private val viewModelJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + viewModelJob)

    init {
        refreshBackupInfo()
    }

    /**
     * 刷新备份信息
     */
    fun refreshBackupInfo() {
        _backupInfo.value = migrationManager.hasBackup()
    }

    /**
     * 执行备份
     */
    fun exportData() {
        if (_state.value == MigrationState.EXPORTING || _state.value == MigrationState.IMPORTING) return

        _state.value = MigrationState.EXPORTING
        _message.value = "正在备份数据..."

        scope.launch {
            val result = migrationManager.exportToFolder()
            when (result) {
                is MigrationResult.Success -> {
                    _state.value = MigrationState.SUCCESS
                    _message.value = result.message
                    refreshBackupInfo()
                }
                is MigrationResult.Error -> {
                    _state.value = MigrationState.ERROR
                    _message.value = result.message
                }
            }
        }
    }

    /**
     * 执行恢复
     */
    fun importData() {
        if (_state.value == MigrationState.EXPORTING || _state.value == MigrationState.IMPORTING) return

        _state.value = MigrationState.IMPORTING
        _message.value = "正在恢复数据..."

        scope.launch {
            val result = migrationManager.importFromFolder()
            when (result) {
                is MigrationResult.Success -> {
                    _state.value = MigrationState.SUCCESS
                    _message.value = result.message
                }
                is MigrationResult.Error -> {
                    _state.value = MigrationState.ERROR
                    _message.value = result.message
                }
            }
        }
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _state.value = MigrationState.IDLE
        _message.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        viewModelJob.cancel()
    }
}
