// 对应 prd.md §3.1.9 v0.8.0 + §A.15.1：LLM Provider → (endpoint, model, key alias) 静态映射
// 三 Provider 走 OpenAI Chat Completions 协议；endpoint / model / Key alias 全部硬编码，UI 不暴露
package com.elder.android.data.llm

import com.elder.android.data.crypto.ApiKeyCipher
import com.elder.android.data.db.LlmProvider

object LlmProviderCatalog {
    fun endpointOf(p: LlmProvider): String = when (p) {
        LlmProvider.MINIMAX -> "https://api.minimax.cn/v1/chat/completions"
        LlmProvider.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
        LlmProvider.DEEPSEEK -> "https://api.deepseek.com/v1/chat/completions"
    }

    fun modelOf(p: LlmProvider): String = when (p) {
        LlmProvider.MINIMAX -> "MiniMax-M3"
        LlmProvider.QWEN -> "qwen-plus"
        LlmProvider.DEEPSEEK -> "deepseek-chat"
    }

    /** 对应 prd.md §5.11 v0.8.0 / §A.15.3：每 Provider 独立 EncryptedSharedPreferences alias */
    fun keyAliasOf(p: LlmProvider): String = when (p) {
        LlmProvider.MINIMAX -> ApiKeyCipher.KEY_MINIMAX_API_KEY_ENC
        LlmProvider.QWEN -> ApiKeyCipher.KEY_QWEN_LLM_API_KEY_ENC
        LlmProvider.DEEPSEEK -> ApiKeyCipher.KEY_DEEPSEEK_LLM_API_KEY_ENC
    }
}
