// 对应 prd.md §3.1.9 v0.8.0 + AGENTS.md §A.15：MiniMax M3 OpenAI 兼容客户端
// 继承 OpenAiLlmClient 基类（§A.15），仅注入 MiniMax 特有 body 字段
// （`thinking.type=disabled` / `reasoning_split=true` / `max_completion_tokens=256`）。
package com.elder.android.data.llm

import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class MiniMaxClient(
    client: okhttp3.OkHttpClient = OpenAiLlmClient.defaultClient(),
    endpoint: String = DEFAULT_ENDPOINT,
    model: String = DEFAULT_MODEL,
) : OpenAiLlmClient(client) {
    override val endpoint: String = endpoint
    override val model: String = model

    override fun extraRequestBody(obj: JsonObjectBuilder) {
        obj.put("thinking", buildJsonObject { put("type", JsonPrimitive("disabled")) })
        obj.put("reasoning_split", JsonPrimitive(true))
        obj.put("max_completion_tokens", JsonPrimitive(256))
    }

    companion object {
        const val DEFAULT_MODEL = "MiniMax-M3"
        const val DEFAULT_ENDPOINT = "https://api.minimax.cn/v1/chat/completions"
    }
}
