// §3.1.9 TTS Provider 子页 ViewModel（v0.7.0 §A.14）
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.db.TtsProvider
import com.elder.android.data.tts.MiniMaxTtsClient
import com.elder.android.data.tts.QwenTtsClient
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TtsProviderUiState(
    val provider: TtsProvider = TtsProvider.MINIMAX,
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val lastTestResult: String? = null,
    val topError: String? = null,
    val savedOk: Boolean = false,
) {
    val allRequiredValid: Boolean get() = apiKey.isNotBlank()
}

class TtsProviderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo

    private val _state = MutableStateFlow(TtsProviderUiState())
    val state: StateFlow<TtsProviderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.current() ?: return@launch
            _state.update {
                it.copy(
                    provider = cfg.ttsProvider,
                    apiKey = cfg.ttsKey(),
                    lastTestResult = cfg.ttsMinimaxLastTestResult,
                )
            }
        }
    }

    fun setProvider(p: TtsProvider) = _state.update { it.copy(provider = p) }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun saveAndBack(onDone: () -> Unit) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            // 对应 §3.1.9：未在顶层初始化配置时，子页也能独立保存 —— 用当前选中的 Provider
            // 反推一份默认 AsrConfig；避免上层未写时这里静默 return 让老人觉得没反应。
            val existing = repo.current()
            val cfg = existing ?: AsrConfig(
                apiKey = if (s.provider == TtsProvider.QWEN) s.apiKey else "",
                ttsMinimaxApiKey = if (s.provider == TtsProvider.MINIMAX) s.apiKey else "",
            )
            val updated = cfg.copy(ttsProvider = s.provider)
            try {
                when (s.provider) {
                    TtsProvider.QWEN -> repo.save(updated.copy(apiKey = s.apiKey))
                    TtsProvider.MINIMAX -> repo.save(updated.copy(ttsMinimaxApiKey = s.apiKey))
                }
                _state.update { it.copy(savedOk = true, topError = null) }
                onDone()
            } catch (t: Throwable) {
                // 对应 §4.8：保存失败 / Keystore 不可用走顶部红条兜底
                _state.update { it.copy(topError = t.message ?: "保存失败") }
            }
        }
    }

    fun test() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, topError = null) }
            val client = when (s.provider) {
                TtsProvider.QWEN -> QwenTtsClient()
                TtsProvider.MINIMAX -> MiniMaxTtsClient()
            }
            val started = System.currentTimeMillis()
            val result = runCatching { client.speak(apiKey = s.apiKey, text = "你好，我是老友。") }
            result.onSuccess {
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"text":"语音连接正常","latency_ms":${System.currentTimeMillis() - started}}""",
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"error":"${e.message ?: "测试失败"}"}""",
                        topError = e.message ?: "语音测试失败",
                    )
                }
            }
        }
    }
}
