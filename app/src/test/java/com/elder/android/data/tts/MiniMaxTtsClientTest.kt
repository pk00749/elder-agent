// §3.1.9 + §A.13 MiniMax T2A TTS 客户端测试：WebSocket 协议 + 错误映射
package com.elder.android.data.tts

import com.elder.android.error.AppError
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MiniMaxTtsClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: MiniMaxTtsClient
    private lateinit var sink: RecordingSink

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        sink = RecordingSink()
        client = MiniMaxTtsClient(
            sinkFactory = { sink },
            client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build(),
            wsUrl = wsUrl(),
        )
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun wsUrl(): String =
        server.url("/t2a_v2").toString().replaceFirst("http://", "ws://")

    private fun typeOf(text: String): String =
        JSONObject(text).optString("event")

    private fun connectedSuccessMessage(): String =
        JSONObject().put("event", "connected_success").toString()

    private fun taskStartedMessage(): String =
        JSONObject().put("event", "task_started").toString()

    private fun taskFinishedMessage(): String =
        JSONObject().put("event", "task_finished").toString()

    private fun audioDataMessage(hex: String): String =
        JSONObject().apply {
            put("data", JSONObject().put("audio", hex))
        }.toString()

    private fun isFinalMessage(): String =
        JSONObject().put("is_final", true).toString()

    private fun taskFailedMessage(code: String, message: String): String =
        JSONObject().apply {
            put("event", "task_failed")
            put("error", JSONObject().apply {
                put("code", code)
                put("message", message)
            })
        }.toString()

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

@Test fun `tts full handshake streams hex audio and returns result`() = runBlocking {
        val taskStartMsg = StringBuilder()
        val taskContinueMsg = StringBuilder()
        val taskFinishMsg = StringBuilder()

        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                // 建连后立刻 push connected_success
                Thread {
                    try {
                        webSocket.send(connectedSuccessMessage())
                    } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "task_start" -> {
                        taskStartMsg.append(text)
                        webSocket.send(taskStartedMessage())
                    }
                    "task_continue" -> {
                        taskContinueMsg.append(text)
                        Thread {
                            try {
                                webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(1, 2, 3, 4))))
                                webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(5, 6, 7, 8))))
                                webSocket.send(isFinalMessage())
                                Thread.sleep(100)
                            } catch (_: Throwable) {}
                        }.start()
                    }
                    "task_finish" -> {
                        taskFinishMsg.append(text)
                        webSocket.send(taskFinishedMessage())
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))

        val result = client.speak(apiKey = "sk-minimax-test", text = "你好，我是老友。")
        assertTrue("expected non-zero total latency", result.totalLatencyMs > 0)
        assertEquals(8, sink.byteCount)

        val startJson = JSONObject(taskStartMsg.toString())
        assertEquals(MiniMaxTtsClient.MINIMAX_TTS_MODEL, startJson.getString("model"))
        val voiceSetting = startJson.getJSONObject("voice_setting")
        assertEquals(MiniMaxTtsClient.MINIMAX_TTS_VOICE_ID, voiceSetting.getString("voice_id"))
        val audioSetting = startJson.getJSONObject("audio_setting")
        assertEquals("pcm", audioSetting.getString("format"))
        assertEquals(MiniMaxTtsClient.SAMPLE_RATE, audioSetting.getInt("sample_rate"))
        assertEquals(1, audioSetting.getInt("channel"))

        val continueJson = JSONObject(taskContinueMsg.toString())
        assertEquals("你好，我是老友。", continueJson.getString("text"))

        assertTrue("expected task_finish to be sent", taskFinishMsg.isNotEmpty())
    }

    @Test fun `tts handshake HTTP 401 maps to TTS_AUTH_FAILED`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            client.speak(apiKey = "expired", text = "hello")
            fail("expected AppError.TtsAuthFailed")
        } catch (e: AppError.TtsAuthFailed) {
            assertEquals(AppError.Code.TTS_AUTH_FAILED, e.code)
        }
    }

    @Test fun `tts task_failed with auth keyword maps to TTS_AUTH_FAILED`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    webSocket.send(taskFailedMessage("invalid_api_key", "api key expired"))
                    webSocket.close(1000, "failed")
                }
            }
        }))
        try {
            client.speak(apiKey = "k", text = "hello")
            fail("expected AppError.TtsAuthFailed")
        } catch (e: AppError.TtsAuthFailed) {
            assertEquals(AppError.Code.TTS_AUTH_FAILED, e.code)
        }
    }

    @Test fun `blank apiKey throws TTS_AUTH_FAILED before WS connect`() = runBlocking {
        try {
            client.speak(apiKey = "", text = "hello")
            fail("expected AppError.TtsAuthFailed")
        } catch (e: AppError.TtsAuthFailed) {
            assertEquals(AppError.Code.TTS_AUTH_FAILED, e.code)
        }
        assertEquals("no WS request should be issued", 0, server.requestCount)
    }

    @Test fun `voice_id model url and audio format match A13`() {
        assertEquals("Cantonese_KindWoman", MiniMaxTtsClient.MINIMAX_TTS_VOICE_ID)
        assertEquals("speech-2.8-hd", MiniMaxTtsClient.MINIMAX_TTS_MODEL)
        assertTrue(
            "WS_URL must point to MiniMax t2a_v2 endpoint",
            MiniMaxTtsClient.WS_URL == "wss://api.minimax.cn/ws/v1/t2a_v2",
        )
        assertEquals(24_000, MiniMaxTtsClient.SAMPLE_RATE)
        assertEquals("pcm", MiniMaxTtsClient.AUDIO_FORMAT)
    }

    private class RecordingSink : PcmSink {
        @Volatile var byteCount: Int = 0
            private set

        override fun play() = Unit
        override fun write(bytes: ByteArray) { byteCount += bytes.size }
        override fun drain() = Unit
        override fun release() = Unit
    }
}
