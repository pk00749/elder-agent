// 对应 AGENTS.md §A.15.5：千问 qwen-plus 客户端测试（MockWebServer）
package com.elder.android.data.llm

import com.elder.android.error.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class QwenLlmClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `parses streamed text`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"好的，"}}]}
                    data: {"choices":[{"delta":{"content":"听到了。"}}]}
                    data: [DONE]
                    """.trimIndent(),
                ),
        )
        val deltas = mutableListOf<String>()
        val result = client().complete(
            apiKey = "key",
            messages = listOf(LlmMessage("user", "你好")),
            tools = emptyList(),
            onDelta = deltas::add,
        )
        assertEquals("好的，听到了。", result.content)
        assertEquals(listOf("好的，", "听到了。"), deltas)
    }

    @Test
    fun `aggregates streamed tool calls`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c1","function":{"name":"ask_clarify","arguments":"{}"}}]}}]}
                    data: [DONE]
                    """.trimIndent(),
                ),
        )
        val result = client().complete(
            apiKey = "key",
            messages = listOf(LlmMessage("user", "?")),
            tools = emptyList(),
        )
        assertEquals(1, result.toolCalls.size)
        assertEquals("ask_clarify", result.toolCalls[0].name)
    }

    @Test
    fun `401 maps to LlmAuthFailed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid api key"}}"""))
        val result = runCatching {
            client().complete("bad", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmAuthFailed)
    }

    @Test
    fun `429 maps to LlmRateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate limit"}}"""))
        val result = runCatching {
            client().complete("key", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmRateLimited)
    }

    @Test
    fun `500 maps to LlmUpstream`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))
        val result = runCatching {
            client().complete("key", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmUpstream)
    }

    @Test
    fun `empty api key throws LlmAuthFailed before request`() = runTest {
        val result = runCatching {
            client().complete("", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmAuthFailed)
        // No request should have hit the server
        assertEquals(0, server.requestCount)
    }

    private fun client() = QwenLlmClient(
        client = OkHttpClient(),
        endpoint = server.url("/compatible-mode/v1/chat/completions").toString(),
    )
}
