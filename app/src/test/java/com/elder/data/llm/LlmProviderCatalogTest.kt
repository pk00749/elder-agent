// 对应 AGENTS.md §A.15.1 / §A.15.5：Provider → (endpoint, model, key alias) 静态映射测试
package com.elder.android.data.llm

import com.elder.android.data.db.LlmProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmProviderCatalogTest {
    @Test
    fun `endpoint resolution for all providers`() {
        assertEquals("https://api.minimax.cn/v1/chat/completions", LlmProviderCatalog.endpointOf(LlmProvider.MINIMAX))
        assertEquals("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", LlmProviderCatalog.endpointOf(LlmProvider.QWEN))
        assertEquals("https://api.deepseek.com/v1/chat/completions", LlmProviderCatalog.endpointOf(LlmProvider.DEEPSEEK))
    }

    @Test
    fun `model resolution for all providers`() {
        assertEquals("MiniMax-M3", LlmProviderCatalog.modelOf(LlmProvider.MINIMAX))
        assertEquals("qwen-plus", LlmProviderCatalog.modelOf(LlmProvider.QWEN))
        assertEquals("deepseek-chat", LlmProviderCatalog.modelOf(LlmProvider.DEEPSEEK))
    }

    @Test
    fun `key alias is unique per provider`() {
        val aliases = LlmProvider.entries.map { LlmProviderCatalog.keyAliasOf(it) }.toSet()
        assertEquals(3, aliases.size)
        assertTrue(aliases.contains("minimax_api_key"))
        assertTrue(aliases.contains("qwen_llm_api_key"))
        assertTrue(aliases.contains("deepseek_llm_api_key"))
    }

    @Test
    fun `endpoints use https for all providers`() {
        for (p in LlmProvider.entries) {
            assertTrue(
                "Provider ${p.raw} endpoint must use https",
                LlmProviderCatalog.endpointOf(p).startsWith("https://"),
            )
        }
    }
}
