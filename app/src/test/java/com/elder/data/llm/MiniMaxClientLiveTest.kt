package com.elder.android.data.llm

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class MiniMaxClientLiveTest {
    private val apiKey: String? =
        System.getProperty("MINIMAX_API_KEY")
            ?: System.getenv("MINIMAX_API_KEY")

    @Test
    fun `minimax m3 returns streamed text`() = runBlocking {
        assumeTrue("MINIMAX_API_KEY not set; skip live LLM", !apiKey.isNullOrBlank())
        val result = MiniMaxClient().complete(
            apiKey = apiKey!!,
            messages = listOf(LlmMessage("user", "只回复：好")),
            tools = emptyList(),
        )
        assertTrue(result.content.isNotBlank())
    }

    @Test
    fun `minimax m3 accepts save_diary tool schema`() = runBlocking {
        assumeTrue("MINIMAX_API_KEY not set; skip live tool call", !apiKey.isNullOrBlank())
        val tool = LlmTool(
            name = "save_diary",
            description = "保存日记",
            parameters = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("object"))
                put("properties", kotlinx.serialization.json.buildJsonObject {
                    put("text", kotlinx.serialization.json.buildJsonObject {
                        put("type", kotlinx.serialization.json.JsonPrimitive("string"))
                    })
                })
                put("required", kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive("text")
                )))
            },
        )
        val result = MiniMaxClient().complete(
            apiKey = apiKey!!,
            messages = listOf(
                LlmMessage("system", "信息足够时必须调用 save_diary。"),
                LlmMessage("user", "今天和老张下棋了。"),
            ),
            tools = listOf(tool),
        )
        assertTrue(result.toolCalls.isNotEmpty() || result.content.isNotBlank())
    }
}
