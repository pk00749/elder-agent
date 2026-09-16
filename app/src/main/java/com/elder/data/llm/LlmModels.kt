package com.elder.android.data.llm

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
