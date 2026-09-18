// §3.1.8 老人端设置（v0.7.0：ASR/TTS Provider 摘要展示）
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.BuildConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.FontScale
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
    val showLogoutConfirm: Boolean = false,
    val loggedOut: Boolean = false,
    val versionName: String = BuildConfig.VERSION_NAME,
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
                        asrConfigured = cfg?.isConfigured == true,
                        asrProviderLabel = providerLabel(cfg?.asrProvider, AsrProvider.BAILIAN),
                        ttsProviderLabel = providerLabel(cfg?.ttsProvider, TtsProvider.MINIMAX),
                    )
                }
            }
        }
    }

    private fun providerLabel(p: AsrProvider?, fallback: AsrProvider): String {
        val effective = p ?: fallback
        return when (effective) {
            AsrProvider.BAILIAN -> "百炼"
            AsrProvider.MINIMAX_REALTIME -> "MiniMax"
        }
    }

    private fun providerLabel(p: TtsProvider?, fallback: TtsProvider): String {
        val effective = p ?: fallback
        return when (effective) {
            TtsProvider.QWEN -> "千问 Kiki"
            TtsProvider.MINIMAX -> "MiniMax"
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

    fun confirmLogout() {
        viewModelScope.launch {
            diaryRepo.clearAll()
            ServiceLocator.interviewRepo.clearAll()
            ServiceLocator.pendingDiaryRepo.clearAll()
            asrRepo.clear()
            metaRepo.clear()
            _uiState.update { it.copy(showLogoutConfirm = false, loggedOut = true) }
        }
    }
}
