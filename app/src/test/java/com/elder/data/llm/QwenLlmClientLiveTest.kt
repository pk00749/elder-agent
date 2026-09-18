// 对应 AGENTS.md §A.15.5：千问 LLM LiveTest（需 -PQWEN_LLM_API_KEY 或 QWEN_LLM_API_KEY 环境变量）
package com.elder.android.data.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class QwenLlmClientLiveTest {
    private val apiKey: String? =
        System.getProperty("QWEN_LLM_API_KEY")
            ?: System.getenv("QWEN_LLM_API_KEY")

    @Test
    fun `qwen plus returns streamed text`() = runBlocking {
        assumeTrue("QWEN_LLM_API_KEY not set; skip live LLM", !apiKey.isNullOrBlank())
        val result = QwenLlmClient().complete(
            apiKey = apiKey!!,
            messages = listOf(LlmMessage("user", "只回复：好")),
            tools = emptyList(),
        )
        assertTrue(result.content.isNotBlank())
    }
}
