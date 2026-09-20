// §3.1.9 AI 服务配置页 ViewModel（v0.8.0 §A.15）
// 顶层页改只读状态总览（§A.15.3）：三个 Provider 入口卡 + 状态展示，Key 输入全部下沉到子页。
// 顶层不再有任何 OutlinedTextField / 保存按钮；本 VM 只负责把 AsrConfig 同步成 UI state。
// v0.8.1 修订：init 改订阅 repo.observe()（修复"子页保存后顶层不刷新"bug），删除孤儿 save()
// 和 6 个 set* 死代码（v0.6.0 顶层输入 Key 时代遗留）。
package com.elder.android.screen.asr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Provider raw → 老人可读中文标签。 */
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

/**
 * 顶层 AI 服务页 UI 状态（v0.8.1 只读总览版）。
 *
 * 不再持有明文 Key —— ApiKeyCipher 加密落盘后顶层无从解密，且 §A.15.3 设计本就让 Key
 * 全部在子页持有。Provider 是否配置过，只用 `providerRaw.isNotBlank()` 粗判；详细 Key
 * 是否填齐由各 Provider 子页 onEnter 时读取 cfg 解密判断（子页能拿到明文）。
 */
data class AsrConfigUiState(
    val asrProviderRaw: String = "",
    val ttsProviderRaw: String = "",
    val llmProviderRaw: String = "",
) {
    val asrProviderDisplay: String get() = displayAsrProvider(asrProviderRaw)
    val ttsProviderDisplay: String get() = displayTtsProvider(ttsProviderRaw)
    val llmProviderDisplay: String get() = displayLlmProvider(llmProviderRaw)

    /** Provider 记录已写入 DB 即认为"已配置"；Key 是否填齐由子页细化。 */
    val asrConfigured: Boolean get() = asrProviderRaw.isNotBlank()
    val ttsConfigured: Boolean get() = ttsProviderRaw.isNotBlank()
    val llmConfigured: Boolean get() = llmProviderRaw.isNotBlank()
}

class AsrConfigViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AsrConfigRepository = ServiceLocator.asrConfigRepo

    private val _state = MutableStateFlow(AsrConfigUiState())
    val state: StateFlow<AsrConfigUiState> = _state.asStateFlow()

    init {
        // v0.8.1 修复：订阅 repo.observe() 而不是只读一次（修"子页保存后顶层不刷新"）
        viewModelScope.launch {
            repo.observe().collect { cfg ->
                applyConfig(cfg)
            }
        }
    }

    private fun applyConfig(c: AsrConfig?) {
        if (c == null) return
        _state.update {
            it.copy(
                asrProviderRaw = c.asrProvider.raw,
                ttsProviderRaw = c.ttsProvider.raw,
                llmProviderRaw = c.llmProvider.raw,
            )
        }
    }
}
