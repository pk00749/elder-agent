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
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.FontScale
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.db.TtsProvider
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
    val showLogoutConfirm: Boolean = false,
    val loggedOut: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
    val topError: String? = null,         // v0.8.1 新增：退出登录失败 / 其他 VM 错误
)

class ElderSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val metaRepo: DeviceMetaRepository = ServiceLocator.deviceMetaRepo
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val diaryRepo = ServiceLocator.diaryRepo

    private val _uiState = MutableStateFlow(ElderSettingsUiState())
    val uiState: StateFlow<ElderSettingsUiState> = _uiState.asStateFlow()

    init {
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
