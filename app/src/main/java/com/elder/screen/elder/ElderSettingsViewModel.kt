// §3.1.8 老人端设置（隐藏入口，v3.0 MVP 版）
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.BuildConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.db.FontScale
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
                _uiState.update { it.copy(asrConfigured = cfg?.isConfigured == true) }
            }
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
            // §3.1.8 MVP：清本地 Room + 清 ASR 配置 + 跳主屏
            diaryRepo.clearAll()
            asrRepo.clear()
            metaRepo.clear()
            _uiState.update { it.copy(showLogoutConfirm = false, loggedOut = true) }
        }
    }
}
