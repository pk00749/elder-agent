// 对应 AGENTS.md §A.15.5：DeepSeek LLM LiveTest（需 -PDEEPSEEK_LLM_API_KEY 或 DEEPSEEK_LLM_API_KEY 环境变量）
package com.elder.android.data.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class DeepSeekLlmClientLiveTest {
    private val apiKey: String? =
        System.getProperty("DEEPSEEK_LLM_API_KEY")
            ?: System.getenv("DEEPSEEK_LLM_API_KEY")

    @Test
    fun `deepseek chat returns streamed text`() = runBlocking {
        assumeTrue("DEEPSEEK_LLM_API_KEY not set; skip live LLM", !apiKey.isNullOrBlank())
        val result = DeepSeekLlmClient().complete(
            apiKey = apiKey!!,
            messages = listOf(LlmMessage("user", "只回复：好")),
            tools = emptyList(),
        )
        assertTrue(result.content.isNotBlank())
    }
}
