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

class MiniMaxClientTest {
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
    fun `parses streamed text into complete content`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"嗯，"}}]}
                    data: {"choices":[{"delta":{"content":"然后呢？"}}]}
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
        assertEquals("嗯，然后呢？", result.content)
        assertEquals(listOf("嗯，", "然后呢？"), deltas)
    }

    @Test
    fun `aggregates streamed tool call fragments`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-1","function":{"name":"save_diary","arguments":"{\"text\":\"今天"}}]}}]}
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"散步\"}"}}]},"finish_reason":"tool_calls"}]}
                    data: [DONE]
                    """.trimIndent(),
                ),
        )
        val result = client().complete(
            apiKey = "key",
            messages = listOf(LlmMessage("user", "今天散步")),
            tools = emptyList(),
        )
        assertEquals(1, result.toolCalls.size)
        assertEquals("save_diary", result.toolCalls[0].name)
        assertTrue(result.toolCalls[0].arguments.contains("今天"))
    }

    @Test
    fun `maps unauthorized response to auth error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))
        val result = runCatching {
            client().complete("bad", listOf(LlmMessage("user", "hi")), emptyList())
        }
        assertTrue(result.exceptionOrNull() is AppError.LlmAuthFailed)
    }

    private fun client() = MiniMaxClient(
        client = OkHttpClient(),
        endpoint = server.url("/v1/chat/completions").toString(),
    )
}
