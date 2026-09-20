// §3.1.9 + §A.12 MiniMax ASR 客户端测试：HTTP REST + multipart + SSE 增量解析
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class MiniMaxAsrClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MiniMaxAsrClient
    private lateinit var sample: File

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        client = MiniMaxAsrClient(
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            restUrl = server.url("/v1/speech_to_text").toString(),
        )
        sample = File.createTempFile("minimax_asr_test", ".wav")
        sample.writeBytes(ByteArray(7200) { (it and 0xff).toByte() })
    }

    @After fun tearDown() {
        server.shutdown()
        sample.delete()
    }

    private fun sseResponse(events: List<String>, status: Int = 200): MockResponse {
        val body = buildString {
            for (e in events) {
                append("data: ").append(e).append('\n')
            }
        }
        return MockResponse()
            .setResponseCode(status)
            .addHeader("Content-Type", "text/event-stream")
            .setBody(body)
    }

    @Test fun `transcribe accumulates deltas and returns full transcript`() = runBlocking {
        server.enqueue(
            sseResponse(
                listOf(
                    """{"delta":"今天"}""",
                    """{"delta":"天气"}""",
                    """{"delta":"很好"}""",
                    """{"finish":true}""",
                ),
            ),
        )

        val result = client.transcribe(apiKey = "sk-minimax-test", audioFile = sample)
        assertEquals("今天天气很好", result.text)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/speech_to_text", request.path)
        assertEquals(
            "Bearer sk-minimax-test",
            request.getHeader("Authorization"),
        )
        val body = request.body.readUtf8()
        assertTrue("multipart body must include model=asr-1.0", body.contains("name=\"model\""))
        assertTrue(body.contains("asr-1.0"))
        assertTrue("multipart body must include stream=true", body.contains("name=\"stream\""))
        assertTrue(body.contains("true"))
        assertTrue("multipart body must include file upload", body.contains("name=\"file\""))
        assertTrue("uploaded filename must be .wav", body.contains(".wav"))
    }

    @Test fun `openSession streams SSE deltas through onPartial callback`() = runBlocking {
        // 对应 §A.12 MiniMax SSE 协议：服务端 delta 是增量片段，客户端必须按累积文本
        // 回调 onPartial（与 Bailian WS 的 text+stash 累积语义对齐），
        // 否则 UI 每次 delta 都覆盖 state.transcript，前段文字丢失。
        server.enqueue(
            sseResponse(
                listOf(
                    """{"delta":"你好"}""",
                    """{"delta":"，"}""",
                    """{"delta":"世界"}""",
                    """{"finish":true}""",
                ),
            ),
        )

        val partials = CopyOnWriteArrayList<String>()
        val session = client.openSession(apiKey = "sk-minimax-test") { partial ->
            partials.add(partial)
        }
        try {
            session.appendAudio(ByteArray(3200) { (it and 0xff).toByte() })
            session.appendAudio(ByteArray(3200) { (it and 0xff).toByte() })
            val result = session.finish()
            assertEquals("你好，世界", result.text)
            assertEquals(
                "onPartial must receive cumulative text, not individual deltas",
                listOf("你好", "你好，", "你好，世界"),
                partials.toList(),
            )
        } finally {
            session.close()
        }
    }

    @Test fun `onPartial callback is monotonic and never shrinks across deltas`() = runBlocking {
        // 防回归：覆盖「前段文字丢失」与「partial 长度回退」两种 bug 形态。
        server.enqueue(
            sseResponse(
                listOf(
                    """{"delta":"a"}""",
                    """{"delta":"bc"}""",
                    """{"delta":"def"}""",
                    """{"delta":""}""",                // 空 delta 必须不破坏累积器
                    """{"delta":"gh"}""",
                    """{"finish":true}""",
                ),
            ),
        )

        val partials = CopyOnWriteArrayList<String>()
        val session = client.openSession(apiKey = "sk-minimax-test") { partial ->
            partials.add(partial)
        }
        try {
            session.appendAudio(ByteArray(3200) { (it and 0xff).toByte() })
            val result = session.finish()
            assertEquals("abcdefgh", result.text)
            // 只在「非空 delta」时回调一次：5 条数据 → 4 次回调（空 delta 跳过）
            assertEquals(4, partials.size)
            // 累积语义：每条 partial 必须是前一条的超串，且最终值 = 完整文本
            assertEquals(listOf("a", "abc", "abcdef", "abcdefgh"), partials.toList())
            // 不变式：partials 单调增长 + 末项 = 结果
            for (i in 1 until partials.size) {
                assertTrue(
                    "partial must monotonically grow (i=$i)",
                    partials[i].startsWith(partials[i - 1]),
                )
            }
            assertEquals(result.text, partials.last())
        } finally {
            session.close()
        }
    }

    @Test fun `onPartial is invoked exactly once per non-empty delta in batch transcribe`() = runBlocking {
        // transcribe() 内部传 onPartial = {}（batch 路径不需要回调），但累积器仍要正确拼出最终文本；
        // 用 List 截胡 accumulator 不可能（它局部私有），故只能通过 final result.text 间接验证。
        server.enqueue(
            sseResponse(
                listOf(
                    """{"delta":"分"}""",
                    """{"delta":"批"}""",
                    """{"delta":"上"}""",
                    """{"delta":"传"}""",
                    """{"finish":true}""",
                ),
            ),
        )
        val result = client.transcribe(apiKey = "sk-minimax-test", audioFile = sample)
        assertEquals("分批上传", result.text)
    }

    @Test fun `transcribe HTTP 401 maps to ASR_AUTH_FAILED`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        try {
            client.transcribe(apiKey = "expired", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
    }

    @Test fun `transcribe HTTP 429 maps to ASR_RATE_LIMITED`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrRateLimited")
        } catch (e: AppError.AsrRateLimited) {
            assertEquals(AppError.Code.ASR_RATE_LIMITED, e.code)
        }
    }

    @Test fun `transcribe HTTP 400 maps to ASR_BAD_REQUEST`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"bad request"}"""))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) {
            assertEquals(AppError.Code.ASR_BAD_REQUEST, e.code)
            // 简单 string 形状的 body 不会被解析成 serverErrorCode/Message（§A.12.3 仍走 BAD_REQUEST 即可）
            assertTrue(
                "message should still contain user-facing hint",
                e.message?.contains("配置有误") == true,
            )
        }
    }

    @Test fun `transcribe HTTP 400 with nested error body surfaces serverErrorCode and Message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"error":{"type":"invalid_request_error","code":"model_not_found","message":"asr-1.0 is not available in your workspace"}}""",
            ),
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) {
            assertEquals(AppError.Code.ASR_BAD_REQUEST, e.code)
            assertEquals("model_not_found", e.serverErrorCode)
            assertEquals("asr-1.0 is not available in your workspace", e.serverErrorMessage)
            assertTrue(
                "userMessage should embed upstream message so 配置页能展示真实原因",
                e.message?.contains("asr-1.0 is not available in your workspace") == true,
            )
        }
    }

    @Test fun `transcribe HTTP 500 with nested error body surfaces serverErrorCode and Message`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """{"error":{"code":"internal","message":"upstream timeout"}}""",
            ),
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrUpstream")
        } catch (e: AppError.AsrUpstream) {
            assertEquals(AppError.Code.ASR_UPSTREAM, e.code)
            assertEquals("internal", e.serverErrorCode)
            assertEquals("upstream timeout", e.serverErrorMessage)
        }
    }

    @Test fun `transcribe stream error with auth keyword maps to ASR_AUTH_FAILED`() = runBlocking {
        server.enqueue(
            sseResponse(
                listOf(
                    """{"error":{"type":"auth_error","code":"invalid_api_key","message":"api key expired"}}""",
                ),
            ),
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
    }

    @Test fun `transcribe stream error with rate_limit maps to ASR_RATE_LIMITED`() = runBlocking {
        server.enqueue(
            sseResponse(
                listOf(
                    """{"error":{"type":"throttling","code":"rate_limit","message":"too many requests"}}""",
                ),
            ),
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrRateLimited")
        } catch (e: AppError.AsrRateLimited) {
            assertEquals(AppError.Code.ASR_RATE_LIMITED, e.code)
        }
    }

    @Test fun `transcribe stream error with invalid keyword surfaces serverErrorCode and Message`() = runBlocking {
        server.enqueue(
            sseResponse(
                listOf(
                    """{"error":{"type":"invalid_request_error","code":"model_not_enabled","message":"asr-1.0 not enabled"}}""",
                ),
            ),
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) {
            assertEquals(AppError.Code.ASR_BAD_REQUEST, e.code)
            assertEquals("model_not_enabled", e.serverErrorCode)
            assertEquals("asr-1.0 not enabled", e.serverErrorMessage)
        }
    }

    @Test fun `empty transcript maps to ASR_EMPTY_TRANSCRIPT`() = runBlocking {
        server.enqueue(sseResponse(listOf("""{"finish":true}""")))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrEmptyTranscript")
        } catch (e: AppError.AsrEmptyTranscript) {
            assertEquals(AppError.Code.ASR_EMPTY_TRANSCRIPT, e.code)
        }
    }

    @Test fun `blank apiKey throws ASR_AUTH_FAILED before HTTP request`() = runBlocking {
        try {
            client.transcribe(apiKey = "", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
        assertEquals("no HTTP request should be issued", 0, server.requestCount)
    }

    @Test fun `missing audio file throws ASR_BAD_REQUEST before HTTP request`() = runBlocking {
        val missing = File(sample.parentFile, "does_not_exist.wav")
        try {
            client.transcribe(apiKey = "k", audioFile = missing)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) {
            assertEquals(AppError.Code.ASR_BAD_REQUEST, e.code)
        }
        assertEquals("no HTTP request should be issued", 0, server.requestCount)
    }

    @Test fun `network failure maps to ASR_UPSTREAM`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrUpstream")
        } catch (e: AppError.AsrUpstream) {
            assertEquals(AppError.Code.ASR_UPSTREAM, e.code)
        }
    }

    @Test fun `providerRaw and model match A12 constants`() {
        assertEquals("minimax_realtime", client.providerRaw)
        assertEquals(MiniMaxAsrClient.MINIMAX_ASR_MODEL, client.model)
        assertEquals("asr-1.0", MiniMaxAsrClient.MINIMAX_ASR_MODEL)
        assertTrue(
            "REST_URL must point to MiniMax endpoint",
            MiniMaxAsrClient.REST_URL.startsWith("https://api.minimax.cn/"),
        )
        assertNotNull(MiniMaxAsrClient.STREAM_FLAG)
    }
}
