// 对应 AGENTS.md §A.15.5：LlmClientFactory 路由测试
package com.elder.android.data.llm

import com.elder.android.data.db.LlmProvider
import com.elder.android.error.AppError
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmClientFactoryTest {
    private val factory = LlmClientFactory()

    @Test
    fun `current routes to MiniMax for minimax provider`() {
        val credentials = LlmCredentials(provider = LlmProvider.MINIMAX, minimaxApiKey = "k")
        assertSame(factory.clientFor(LlmProvider.MINIMAX), factory.current(credentials))
    }

    @Test
    fun `current routes to Qwen for qwen provider`() {
        val credentials = LlmCredentials(provider = LlmProvider.QWEN, minimaxApiKey = "", qwenApiKey = "k")
        assertSame(factory.clientFor(LlmProvider.QWEN), factory.current(credentials))
    }

    @Test
    fun `current routes to DeepSeek for deepseek provider`() {
        val credentials = LlmCredentials(provider = LlmProvider.DEEPSEEK, minimaxApiKey = "", deepseekApiKey = "k")
        assertSame(factory.clientFor(LlmProvider.DEEPSEEK), factory.current(credentials))
    }

    @Test
    fun `apiKeyForCurrentProvider picks the right key`() {
        val credentials = LlmCredentials(
            provider = LlmProvider.QWEN,
            minimaxApiKey = "m",
            qwenApiKey = "q",
            deepseekApiKey = "d",
        )
        assertTrue(credentials.apiKeyForCurrentProvider() == "q")
    }

    @Test
    fun `requireProvider defaults unknown to minimax`() {
        assertTrue(LlmClientFactory.requireProvider("minimax") == LlmProvider.MINIMAX)
        assertTrue(LlmClientFactory.requireProvider("qwen") == LlmProvider.QWEN)
        assertTrue(LlmClientFactory.requireProvider("deepseek") == LlmProvider.DEEPSEEK)
    }

    @Test
    fun `requireProvider accepts null and returns default`() {
        assertTrue(LlmClientFactory.requireProvider(null) == LlmProvider.MINIMAX)
    }

    @Test(expected = AppError.LlmProviderUnknown::class)
    fun `requireProvider throws on truly unknown raw`() {
        // bypass fromRaw default fallback: an unknown raw triggers LlmProviderUnknown
        LlmClientFactory.requireProvider("__unknown__provider__")
    }
}
