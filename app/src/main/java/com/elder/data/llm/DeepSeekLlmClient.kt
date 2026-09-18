// 对应 prd.md §3.1.9 v0.8.0 + AGENTS.md §A.15：DeepSeek deepseek-chat OpenAI 兼容客户端
// 与 MiniMax / 千问 共用 OpenAiLlmClient 基类；不注入供应商特有字段
package com.elder.android.data.llm

class DeepSeekLlmClient(
    client: okhttp3.OkHttpClient = OpenAiLlmClient.defaultClient(),
    endpoint: String = DEFAULT_ENDPOINT,
    model: String = DEFAULT_MODEL,
) : OpenAiLlmClient(client) {
    override val endpoint: String = endpoint
    override val model: String = model

    companion object {
        const val DEFAULT_MODEL = "deepseek-chat"
        const val DEFAULT_ENDPOINT = "https://api.deepseek.com/v1/chat/completions"
    }
}
