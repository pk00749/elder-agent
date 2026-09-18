// §3.1.9 AI 服务配置页 ViewModel（v0.8.0 §A.15）
// 顶层页承载 ASR / TTS / LLM 三 Provider 入口卡；每个 Provider 的 Key + 测试由 Provider 子页承载。
// v0.8.0 起 LLM 顶层只显示 Provider 名称 + 「已/未配置」状态，Key 输入全部下沉到子页。
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AsrConfigUiState(
    val apiKey: String = "",
    val minimaxApiKey: String = "",
    val ttsMinimaxApiKey: String = "",
    val qwenLlmApiKey: String = "",
    val deepseekLlmApiKey: String = "",
    val asrProviderRaw: String = "",
    val ttsProviderRaw: String = "",
    val llmProviderRaw: String = "",
    val lastTestResult: String? = null,
    val minimaxLastTestResult: String? = null,
    val ttsMinimaxLastTestResult: String? = null,
    val qwenLlmLastTestResult: String? = null,
    val deepseekLlmLastTestResult: String? = null,
    val isSaving: Boolean = false,
    val savedOk: Boolean = false,
    val topError: String? = null,
) {
    val asrProviderDisplay: String get() = displayAsrProvider(asrProviderRaw)
    val ttsProviderDisplay: String get() = displayTtsProvider(ttsProviderRaw)
    /** 对应 §A.15：LLM Provider 显示名（千问 qwen-plus / MiniMax M3 / DeepSeek deepseek-chat）。 */
    val llmProviderDisplay: String get() = displayLlmProvider(llmProviderRaw)

    /** 对应 prd.md §3.1.9 v0.8.0：三 Provider 全配齐才能保存。 */
    val allRequiredValid: Boolean
        get() = asrKey().isNotBlank() &&
            llmKey().isNotBlank() &&
            ttsKey().isNotBlank()

    /** 当前 ASR Provider 所需 Key（与 AsrConfig.asrKey() 同语义）。 */
    fun asrKey(): String = when (asrProviderRaw.ifBlank { "minimax_realtime" }) {
        "bailian" -> apiKey
        else -> minimaxApiKey   // minimax_realtime
    }

    /** 当前 LLM Provider 所需 Key（与 AsrConfig.llmKey() 同语义）。 */
    fun llmKey(): String = when (llmProviderRaw.ifBlank { "minimax" }) {
        "qwen" -> qwenLlmApiKey
        "deepseek" -> deepseekLlmApiKey
        else -> minimaxApiKey   // minimax
    }

    /** 当前 TTS Provider 所需 Key。 */
    fun ttsKey(): String = when (ttsProviderRaw.ifBlank { "minimax" }) {
        "qwen" -> apiKey
        else -> ttsMinimaxApiKey    // minimax
    }

    companion object {
        private fun displayAsrProvider(raw: String): String = when (raw.ifBlank { "minimax_realtime" }) {
            "bailian" -> "百炼"
            "minimax_realtime" -> "MiniMax"
            else -> raw
        }
        private fun displayTtsProvider(raw: String): String = when (raw.ifBlank { "minimax" }) {
            "qwen" -> "千问 Kiki"
            "minimax" -> "MiniMax"
            else -> raw
        }
        private fun displayLlmProvider(raw: String): String = when (raw.ifBlank { "minimax" }) {
            "minimax" -> "MiniMax M3"
            "qwen" -> "千问 qwen-plus"
            "deepseek" -> "DeepSeek"
            else -> raw
        }
    }
}

class AsrConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo

    private val _state = MutableStateFlow(AsrConfigUiState())
    val state: StateFlow<AsrConfigUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.current()?.let { applyConfig(it) }
        }
    }

    private fun applyConfig(c: AsrConfig) {
        _state.update {
            it.copy(
                apiKey = c.apiKey,
                minimaxApiKey = c.minimaxApiKey,
                ttsMinimaxApiKey = c.ttsMinimaxApiKey,
                qwenLlmApiKey = c.qwenLlmApiKey,
                deepseekLlmApiKey = c.deepseekLlmApiKey,
                asrProviderRaw = c.asrProvider.raw,
                ttsProviderRaw = c.ttsProvider.raw,
                llmProviderRaw = c.llmProvider.raw,
                lastTestResult = c.lastTestResult,
                minimaxLastTestResult = c.minimaxLastTestResult,
                ttsMinimaxLastTestResult = c.ttsMinimaxLastTestResult,
                qwenLlmLastTestResult = c.qwenLlmLastTestResult,
                deepseekLlmLastTestResult = c.deepseekLlmLastTestResult,
            )
        }
    }

    fun setMinimaxApiKey(v: String) = _state.update { it.copy(minimaxApiKey = v) }

    /** Provider 子页回填：AsrProviderScreen 调用，把 ASR Provider raw 写到 state。 */
    fun setAsrProvider(raw: String) = _state.update { it.copy(asrProviderRaw = raw) }

    /** Provider 子页回填：TtsProviderScreen 调用，把 TTS Provider raw 写到 state。 */
    fun setTtsProvider(raw: String) = _state.update { it.copy(ttsProviderRaw = raw) }

    /** Provider 子页回填：LlmProviderScreen 调用，把 LLM Provider raw 写到 state（§A.15）。 */
    fun setLlmProvider(raw: String) = _state.update { it.copy(llmProviderRaw = raw) }

    fun setTtsMinimaxApiKey(v: String) = _state.update { it.copy(ttsMinimaxApiKey = v) }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun save(onDone: () -> Unit = {}) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 4 份 Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, topError = null) }
            try {
                val cfg = AsrConfig(
                    apiKey = s.apiKey,
                    minimaxApiKey = s.minimaxApiKey,
                    ttsMinimaxApiKey = s.ttsMinimaxApiKey,
                    qwenLlmApiKey = s.qwenLlmApiKey,
                    deepseekLlmApiKey = s.deepseekLlmApiKey,
                    asrProvider = com.elder.android.data.db.AsrProvider.fromRaw(s.asrProviderRaw.ifBlank { "minimax_realtime" }),
                    ttsProvider = com.elder.android.data.db.TtsProvider.fromRaw(s.ttsProviderRaw.ifBlank { "minimax" }),
                    llmProvider = com.elder.android.data.db.LlmProvider.fromRaw(s.llmProviderRaw.ifBlank { "minimax" }),
                    lastTestResult = s.lastTestResult,
                    minimaxLastTestResult = s.minimaxLastTestResult,
                    ttsMinimaxLastTestResult = s.ttsMinimaxLastTestResult,
                    qwenLlmLastTestResult = s.qwenLlmLastTestResult,
                    deepseekLlmLastTestResult = s.deepseekLlmLastTestResult,
                )
                repo.save(cfg)
                _state.update { it.copy(isSaving = false, savedOk = true) }
                onDone()
            } catch (t: Throwable) {
                _state.update { it.copy(isSaving = false, topError = t.message ?: "保存失败") }
            }
        }
    }
}
