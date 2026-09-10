// §3.1.9 + §A.1.b 阿里云百炼 ASR 客户端测试：WebSocket 协议 + 错误映射
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

// 纯 JVM 单测：org.json:json:20240303 走 testImplementation，JSONObject() 在 JVM 上有真实现；
// §A.8 WebSocket 协议 mock 由 MockWebServer 兜底，不依赖 Android framework；不需要 Robolectric
class AsrApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AsrApiClient
    private lateinit var sample: File

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = AsrApiClient(
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            wsUrl = wsUrl(),
        )
        sample = File.createTempFile("asr_test", ".m4a")
        sample.writeBytes(ByteArray(64) { 0x42 })
    }

    @After fun tearDown() {
        server.shutdown()
        sample.delete()
    }

    private fun wsUrl(): String =
        server.url("/inference").toString().replaceFirst("http://", "ws://")

    /** Mock 服务端，按 DashScope Qwen-Audio-ASR 协议回 result-generated + task-finished */
    private fun enqueueBailianOk(text: String, confidence: Double = 0.95) {
        val resultMsg = """
            {"header":{"action":"result-generated","task_id":"x","status_code":200},
             "payload":{"output":{"transcription":{"text":"$text","sentence_end":true,"confidence":$confidence}}}}
        """.trimIndent()
        val finishMsg = """{"header":{"action":"task-finished","task_id":"x","status_code":200}}"""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "continue-task" -> webSocket.send(resultMsg)
                    "finish-task" -> {
                        webSocket.send(finishMsg)
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
    }

    private fun enqueueBailianFail(statusCode: Int) {
        val failMsg = """{"header":{"action":"task-failed","task_id":"x","status_code":$statusCode}}"""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                webSocket.send(failMsg)
                webSocket.close(1000, "failed")
            }
        }))
    }

    private fun actionOf(text: String): String =
        Regex("\"action\":\"([^\"]+)\"").find(text)?.groupValues?.getOrNull(1).orEmpty()

    @Test fun `transcribe returns text from result-generated and finishes`() = runTest {
        enqueueBailianOk("今天和老张下棋赢了", 0.91)
        val r = client.transcribe(apiKey = "sk-bailian-test", audioFile = sample)
        assertEquals("今天和老张下棋赢了", r.text)
        assertEquals(0.91f, r.confidence!!, 0.001f)
        val recorded = server.takeRequest()
        assertEquals("Bearer sk-bailian-test", recorded.getHeader("Authorization"))
        // MockWebServer 把 WS upgrade 记录为 GET
        assertEquals("GET", recorded.method)
    }

    @Test fun `task-failed 401 maps to ASR_AUTH_FAILED`() = runTest {
        enqueueBailianFail(401)
        try {
            client.transcribe(apiKey = "bad", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
    }

    @Test fun `task-failed 429 maps to ASR_RATE_LIMITED`() = runTest {
        enqueueBailianFail(429)
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrRateLimited")
        } catch (e: AppError.AsrRateLimited) { /* ok */ }
    }

    @Test fun `task-failed 422 maps to ASR_BAD_REQUEST`() = runTest {
        enqueueBailianFail(422)
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) { /* ok */ }
    }

    @Test fun `task-failed 502 maps to ASR_UPSTREAM`() = runTest {
        enqueueBailianFail(502)
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrUpstream")
        } catch (e: AppError.AsrUpstream) { /* ok */ }
    }

    @Test fun `empty transcript maps to ASR_EMPTY_TRANSCRIPT`() = runTest {
        val emptyMsg = """
            {"header":{"action":"result-generated","task_id":"x","status_code":200},
             "payload":{"output":{"transcription":{"text":"","sentence_end":true}}}}
        """.trimIndent()
        val finishMsg = """{"header":{"action":"task-finished","task_id":"x","status_code":200}}"""
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (actionOf(text)) {
                    "continue-task" -> webSocket.send(emptyMsg)
                    "finish-task" -> {
                        webSocket.send(finishMsg)
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrEmptyTranscript")
        } catch (e: AppError.AsrEmptyTranscript) { /* ok */ }
    }

    @Test fun `blank apiKey throws ASR_AUTH_FAILED before WS connect`() = runTest {
        try {
            client.transcribe(apiKey = "", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) { /* ok */ }
    }
}
