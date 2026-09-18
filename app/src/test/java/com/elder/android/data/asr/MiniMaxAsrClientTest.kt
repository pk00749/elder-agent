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
            assertEquals(listOf("你好", "，", "世界"), partials.toList())
        } finally {
            session.close()
        }
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
