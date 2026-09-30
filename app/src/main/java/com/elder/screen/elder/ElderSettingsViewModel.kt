// §3.1.8 老人端设置（v0.7.0 + v0.8.1 整改）
// v0.8.1 整改：
//   - 新增 llmProviderLabel / topError 字段
//   - confirmLogout() 加 try-catch（任意 repo 抛异常时不卡 dialog）
//   - 修 providerLabel 重载歧义（拆成不同方法名）
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.BuildConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.FontScale
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.db.TtsProvider
import com.elder.android.data.export.ElderSaveModeRepository
import com.elder.android.data.export.SaveMode
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ElderSettingsUiState(
    val fontScale: FontScale = FontScale.DEFAULT,
    val ttsEnabled: Boolean = true,
    val asrConfigured: Boolean = false,
    val asrProviderLabel: String = "",
    val ttsProviderLabel: String = "",
    val llmProviderLabel: String = "",   // v0.8.1 新增
    val ossConfigured: Boolean = false, // v0.10.0 §6 同步到云
    val ossBucket: String = "",          // v0.10.0 §6 已配置时显示 Bucket
    // v0.11.0 §3.1: 当前保存方式(默认 LOCAL)
    val saveMode: SaveMode = SaveMode.DEFAULT,
    // v0.11.0 §3.1: 是否显示保存方式选择 dialog
    val showSaveModeDialog: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val loggedOut: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
    val topError: String? = null,         // v0.8.1 新增：退出登录失败 / 其他 VM 错误
)

class ElderSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val metaRepo: DeviceMetaRepository = ServiceLocator.deviceMetaRepo
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val diaryRepo = ServiceLocator.diaryRepo
    // v0.11.0 §3.1: 保存方式 prefs
    private val saveModeRepo: ElderSaveModeRepository = ServiceLocator.saveModeRepo

    private val _uiState = MutableStateFlow(ElderSettingsUiState())
    val uiState: StateFlow<ElderSettingsUiState> = _uiState.asStateFlow()

    init {
        // v0.11.0 §3.1: 同步加载保存方式 prefs(独立文件;不需要协程)
        _uiState.update { it.copy(saveMode = saveModeRepo.current()) }
        viewModelScope.launch {
            val meta = metaRepo.ensureInitialized()
            _uiState.update { it.copy(fontScale = meta.fontScale, ttsEnabled = meta.ttsEnabled) }
        }
        viewModelScope.launch {
            asrRepo.observe().collect { cfg ->
                _uiState.update {
                    it.copy(
                        asrConfigured = cfg?.let { c ->
                            c.asrProvider.raw.isNotBlank() &&
                                c.ttsProvider.raw.isNotBlank() &&
                                c.llmProvider.raw.isNotBlank()
                        } ?: false,
                        asrProviderLabel = asrLabel(cfg?.asrProvider, AsrProvider.MINIMAX_REALTIME),
                        ttsProviderLabel = ttsLabel(cfg?.ttsProvider, TtsProvider.MINIMAX),
                        llmProviderLabel = llmLabel(cfg?.llmProvider, LlmProvider.MINIMAX),   // v0.8.1
                    )
                }
            }
        }
        // v0.10.0 §6: 读 oss_config 单行表;暴露给 SettingRowOss
        viewModelScope.launch {
            ElderDatabase.get(app).ossConfigDao().let { dao ->
                // 一次性读,不 observe(oss_config 写入频率低;简化 UI 同步)
                val cfg = dao.get()
                _uiState.update {
                    it.copy(
                        ossConfigured = cfg != null,
                        ossBucket = cfg?.bucket.orEmpty(),
                    )
                }
            }
        }
    }

    // v0.8.1 整改：拆成不同方法名，避免与 ttsLabel 重载歧义
    private fun asrLabel(p: AsrProvider?, fallback: AsrProvider): String {
        val effective = p ?: fallback
        return when (effective) {
            AsrProvider.BAILIAN -> "百炼"
            AsrProvider.MINIMAX_REALTIME -> "MiniMax"
        }
    }

    private fun ttsLabel(p: TtsProvider?, fallback: TtsProvider): String {
        val effective = p ?: fallback
        return when (effective) {
            TtsProvider.QWEN -> "千问 Kiki"
            TtsProvider.MINIMAX -> "MiniMax"
        }
    }

    private fun llmLabel(p: LlmProvider?, fallback: LlmProvider): String {
        val effective = p ?: fallback
        return when (effective) {
            LlmProvider.MINIMAX -> "MiniMax M3"
            LlmProvider.QWEN -> "千问 qwen-plus"
            LlmProvider.DEEPSEEK -> "DeepSeek"
        }
    }

    fun setFontScale(scale: FontScale) {
        viewModelScope.launch { metaRepo.updateFontScale(scale) }
        _uiState.update { it.copy(fontScale = scale) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch { metaRepo.updateTtsEnabled(enabled) }
        _uiState.update { it.copy(ttsEnabled = enabled) }
    }

    // v0.11.0 §3.1: 显示 / 关闭保存方式 dialog
    fun showSaveModeDialog() {
        _uiState.update { it.copy(showSaveModeDialog = true) }
    }

    fun dismissSaveModeDialog() {
        _uiState.update { it.copy(showSaveModeDialog = false) }
    }

    // v0.11.0 §3.1: 设置保存方式;持久化到 prefs(独立文件 elder_save_mode)
    fun setSaveMode(mode: SaveMode) {
        saveModeRepo.setMode(mode)
        _uiState.update { it.copy(saveMode = mode, showSaveModeDialog = false) }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirm = true) }
    }

    fun cancelLogout() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
    }

    // v0.8.1 整改：try-catch 包装（任一 repo 抛异常时不卡 dialog，给老人错误反馈）
    fun confirmLogout() {
        viewModelScope.launch {
            runCatching {
                diaryRepo.clearAll()
                ServiceLocator.interviewRepo.clearAll()
                ServiceLocator.pendingDiaryRepo.clearAll()
                asrRepo.clear()
                metaRepo.clear()
            }.onSuccess {
                _uiState.update { it.copy(showLogoutConfirm = false, loggedOut = true, topError = null) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        showLogoutConfirm = false,
                        loggedOut = false,
                        topError = "清空失败：${e.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(topError = null) }
}
