// 对应 AGENTS.md §A.15.5：DeepSeek deepseek-chat 客户端测试（MockWebServer）
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

class DeepSeekLlmClientTest {
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
                    data: {"choices":[{"delta":{"content":"嗯，"}}]}
                    data: {"choices":[{"delta":{"content":"好的。"}}]}
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
        assertEquals("嗯，好的。", result.content)
    }

    @Test
    fun `401 maps to LlmAuthFailed`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val result = runCatching {
            client().complete("bad", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmAuthFailed)
    }

    @Test
    fun `429 maps to LlmRateLimited`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"message":"rate"}}"""))
        val result = runCatching {
            client().complete("key", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmRateLimited)
    }

    @Test
    fun `500 maps to LlmUpstream`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
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
        assertEquals(0, server.requestCount)
    }

    private fun client() = DeepSeekLlmClient(
        client = OkHttpClient(),
        endpoint = server.url("/v1/chat/completions").toString(),
    )
}
