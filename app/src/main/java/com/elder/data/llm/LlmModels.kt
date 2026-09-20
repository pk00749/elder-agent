package com.elder.android.data.llm

import com.elder.android.data.db.LlmProvider
import kotlinx.serialization.json.JsonObject

data class LlmMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<LlmToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class LlmTool(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class LlmResult(
    val content: String,
    val toolCalls: List<LlmToolCall> = emptyList(),
)

interface LlmClient {
    suspend fun complete(
        apiKey: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        onDelta: suspend (String) -> Unit = {},
    ): LlmResult
}

/**
 * v0.8.0 多 Provider 凭据集合。
 *
 * InterviewAgent / LlmClientFactory 用同一份凭据根据当前 [provider] 路由到对应 Client + 取对应 Key。
 * Provider 默认 `LlmProvider.MINIMAX`；`qwenApiKey` / `deepseekApiKey` 默认空串（与 v0.7.0 单 Key 兼容）。
 */
data class LlmCredentials(
    val provider: LlmProvider,
    val minimaxApiKey: String,
    val qwenApiKey: String = "",
    val deepseekApiKey: String = "",
) {
    /** 当前 Provider 所需 Key；UI 只展示对应 Key 输入框（与 AsrConfig.llmKey() 一致）。 */
    fun apiKeyForCurrentProvider(): String = when (provider) {
        LlmProvider.MINIMAX -> minimaxApiKey
        LlmProvider.QWEN -> qwenApiKey
        LlmProvider.DEEPSEEK -> deepseekApiKey
    }
}
