// 对应 prd.md §3.1.9 v0.8.0 + AGENTS.md §A.15：千问 qwen-plus OpenAI 兼容客户端
// 走 DashScope `compatible-mode` 端点；不注入 MiniMax 特有字段；与 MiniMax / DeepSeek 共用基类
package com.elder.android.data.llm

class QwenLlmClient(
    client: okhttp3.OkHttpClient = OpenAiLlmClient.defaultClient(),
    endpoint: String = DEFAULT_ENDPOINT,
    model: String = DEFAULT_MODEL,
) : OpenAiLlmClient(client) {
    override val endpoint: String = endpoint
    override val model: String = model

    companion object {
        const val DEFAULT_MODEL = "qwen-plus"
        const val DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    }
}
