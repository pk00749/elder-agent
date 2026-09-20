// 对应 prd.md §3.1.9 v0.8.0 + AGENTS.md §A.15：LLM 客户端工厂
// 按 `LlmCredentials.provider` 路由到 MiniMax / Qwen / DeepSeek 客户端；
// InterviewAgent 通过 `LlmClientFactory.current(credentials)` 拿到当前 Provider 对应 client。
package com.elder.android.data.llm

import com.elder.android.data.db.LlmProvider
import com.elder.android.error.AppError

/**
 * LLM Client 工厂（v0.8.0）。
 *
 * 三 Provider 共享 `OkHttpClient` 实例（OkHttp 推荐复用连接池）；每个 Provider 持有自己的
 * `OpenAiLlmClient` 子类实例，endpoint / model 通过 `LlmProviderCatalog` 注入。
 *
 * **不可**在路由式 UI 代码直接构造供应商 Client —— 必须经本工厂。
 */
open class LlmClientFactory(
    private val minimaxClient: MiniMaxClient = MiniMaxClient(),
    private val qwenClient: QwenLlmClient = QwenLlmClient(),
    private val deepseekClient: DeepSeekLlmClient = DeepSeekLlmClient(),
) {
    /** 按 [credentials.provider] 返回对应客户端；委托 [clientFor] 便于测试 override。 */
    open fun current(credentials: LlmCredentials): LlmClient = clientFor(credentials.provider)

    /** 测试用：直接按 Provider 取对应 client（不依赖 credentials）。 */
    open fun clientFor(provider: LlmProvider): LlmClient = when (provider) {
        LlmProvider.MINIMAX -> minimaxClient
        LlmProvider.QWEN -> qwenClient
        LlmProvider.DEEPSEEK -> deepseekClient
    }

    companion object {
        /**
         * 解析 Provider raw → 枚举；不识别值走默认 `MINIMAX`，与 AsrProvider.fromRaw 一致。
         * `LlmProviderUnknown` 仅在工厂内部 `current()` 已被消费后、provider raw 与 enum 漂移时抛。
         */
        fun requireProvider(raw: String?): LlmProvider =
            LlmProvider.fromRaw(raw).also {
                if (it.raw != raw && raw != null) throw AppError.LlmProviderUnknown(raw)
            }
    }
}
