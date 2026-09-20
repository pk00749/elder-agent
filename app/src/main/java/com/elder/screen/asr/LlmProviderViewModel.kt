// §3.1.9 v0.8.0 + §A.15：LLM Provider 子页 ViewModel（v0.8.1 整改）
// Provider 选项 + 当前 Provider 所需 Key + 测试按钮。
// v0.8.1 整改：
//   - 加 cancelTest()
//   - setProvider() 切 Provider 时清空 lastTestResult
//   - 测试 prompt 改中文"用一句话介绍你自己"（替代英文"hi"）
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmMessage
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import kotlinx.coroutines.Job
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

    // v0.8.1：保存测试协程
    private var testJob: Job? = null

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

    // v0.8.1：切 Provider 时清空 lastTestResult
    fun setProvider(p: LlmProvider) {
        _state.update {
            if (it.provider != p) it.copy(provider = p, lastTestResult = null)
            else it
        }
    }

    fun setApiKey(v: String) = _state.update { it.copy(apiKey = v) }

    fun dismissError() = _state.update { it.copy(topError = null) }

    fun saveAndBack(onDone: () -> Unit) {
        val s = _state.value
        if (!s.allRequiredValid) {
            _state.update { it.copy(topError = "请先填 Key") }
            return
        }
        viewModelScope.launch {
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
        // v0.8.1：取消上一个未完的测试
        testJob?.cancel()
        testJob = viewModelScope.launch {
            _state.update { it.copy(isTesting = true, topError = null) }
            val client = factory.clientFor(s.provider)
            val started = System.currentTimeMillis()
            val result = runCatching {
                client.complete(
                    apiKey = s.apiKey,
                    // v0.8.1 整改：测试 prompt 改中文 "用一句话介绍你自己"，贴近老人使用场景
                    messages = listOf(LlmMessage(role = "user", content = "用一句话介绍你自己")),
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
                val msg = (e as? AppError)?.message ?: e.message ?: "测试失败"
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

    // v0.8.1：测试取消入口
    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _state.update { it.copy(isTesting = false) }
    }

    /** 测试用：暴露 factory，便于 mock。 */
    @Suppress("unused")
    internal fun factoryForTest(): LlmClientFactory = factory

    override fun onCleared() {
        testJob?.cancel()
        super.onCleared()
    }
}
