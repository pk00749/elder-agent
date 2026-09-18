// §3.1.9 v0.8.0 + §A.15：LLM Provider 子页 ViewModel
// Provider 选项 + 当前 Provider 所需 Key + 测试按钮。
// Provider 切换后通过 [saveAndBack] 落库；测试按钮调用当前 Provider 对应 LlmClient。
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.crypto.ApiKeyCipher
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LlmProviderUiState(
    val provider: LlmProvider = LlmProvider.MINIMAX,
    val apiKey: String = "",
    val isTesting: Boolean = false,
    val lastTestResult: String? = null,
    val topError: String? = null,
    val savedOk: Boolean = false,
) {
    val allRequiredValid: Boolean get() = apiKey.isNotBlank()
}

class LlmProviderViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val factory: LlmClientFactory = ServiceLocator.llmClientFactory

    private val _state = MutableStateFlow(LlmProviderUiState())
    val state: StateFlow<LlmProviderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = repo.current() ?: return@launch
            _state.update {
                it.copy(
                    provider = cfg.llmProvider,
                    apiKey = cfg.llmKey(),
                    lastTestResult = when (cfg.llmProvider) {
                        LlmProvider.QWEN -> cfg.qwenLlmLastTestResult
                        LlmProvider.DEEPSEEK -> cfg.deepseekLlmLastTestResult
                        LlmProvider.MINIMAX -> cfg.minimaxLastTestResult
                    },
                )
            }
        }
    }

    fun setProvider(p: LlmProvider) = _state.update { it.copy(provider = p) }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun saveAndBack(onDone: () -> Unit) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            // 对应 §3.1.9 v0.8.0：未在顶层初始化配置时，子页也能独立保存
            val existing = repo.current()
            val cfg = existing ?: AsrConfig(apiKey = "")
            val updated = cfg.copy(llmProvider = s.provider)
            try {
                val toSave = when (s.provider) {
                    LlmProvider.MINIMAX -> updated.copy(minimaxApiKey = s.apiKey)
                    LlmProvider.QWEN -> updated.copy(qwenLlmApiKey = s.apiKey)
                    LlmProvider.DEEPSEEK -> updated.copy(deepseekLlmApiKey = s.apiKey)
                }
                repo.save(toSave)
                _state.update { it.copy(savedOk = true, topError = null) }
                onDone()
            } catch (t: Throwable) {
                _state.update { it.copy(topError = t.message ?: "保存失败") }
            }
        }
    }

    /** 测试按钮：调当前 Provider 对应 client 发最小 prompt（§A.15.1 / §A.15.5）。 */
    fun test() {
        val s = _state.value
        if (s.apiKey.isBlank()) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, topError = null) }
            val client = factory.clientFor(s.provider)
            val started = System.currentTimeMillis()
            val result = runCatching {
                client.complete(
                    apiKey = s.apiKey,
                    messages = listOf(
                        com.elder.android.data.llm.LlmMessage(role = "user", content = "hi"),
                    ),
                    tools = emptyList(),
                )
            }
            result.onSuccess {
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"text":"✓ 连接正常","latency_ms":${System.currentTimeMillis() - started}}""",
                    )
                }
            }.onFailure { e ->
                val msg = when (e) {
                    is AppError -> e.message ?: "测试失败"
                    else -> e.message ?: "测试失败"
                }
                _state.update {
                    it.copy(
                        isTesting = false,
                        lastTestResult = """{"error":"$msg"}""",
                        topError = msg,
                    )
                }
            }
        }
    }

    /** 测试用：暴露 factory，便于 mock。 */
    @Suppress("unused")
    internal fun factoryForTest(): LlmClientFactory = factory
}
