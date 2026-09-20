// 对应 prd.md §3.1.9 v0.8.0 + §5.11 v0.8.0：LLM Provider 三选一（千问 / MiniMax / DeepSeek）
// 默认 `minimax`，与 PRD §5.11 默认值一致；千问 / DeepSeek 作为可选回滚路径保留
package com.elder.android.data.db

enum class LlmProvider(val raw: String) {
    MINIMAX("minimax"),
    QWEN("qwen"),
    DEEPSEEK("deepseek");

    companion object {
        fun fromRaw(raw: String?): LlmProvider =
            entries.firstOrNull { it.raw == raw } ?: MINIMAX
    }
}
