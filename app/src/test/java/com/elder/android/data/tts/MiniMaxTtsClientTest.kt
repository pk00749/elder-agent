// §3.1.9 + §A.13 MiniMax T2A TTS 客户端测试：v0.12.0 切 bidi
//   - 完整握手 → audio_setting 不含 bitrate + 含 language_boost
//   - 错误映射 → 读 base_resp.status_code int（不是 error.code/message）
//   - 新事件 + 4 条（sentence_start / sentence_end / task_canceled / task_flushed 容忍）
//   - WS_URL 含 _t2a_v2_bidi
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
import org.junit.Assert.assertFalse
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
        server.url("/ws/v1/t2a_v2_bidi").toString().replaceFirst("http://", "ws://")

    private fun typeOf(text: String): String =
        JSONObject(text).optString("event")

    private fun connectedSuccessMessage(): String =
        JSONObject().put("event", "connected_success").toString()

    private fun taskStartedMessage(): String =
        JSONObject().put("event", "task_started").toString()

    private fun taskFinishedMessage(): String =
        JSONObject().put("event", "task_finished").toString()

    private fun audioDataMessage(hex: String, isFinal: Boolean = false): String =
        JSONObject().apply {
            put("data", JSONObject().put("audio", hex))
            put("is_final", isFinal)
        }.toString()

    private fun isFinalMessage(): String =
        JSONObject().put("is_final", true).toString()

    private fun taskFailedMessage(statusCode: Int, statusMsg: String = "XXXXX"): String =
        JSONObject().apply {
            put("event", "task_failed")
            put("base_resp", JSONObject().apply {
                put("status_code", statusCode)
                put("status_msg", statusMsg)
            })
        }.toString()

    private fun sentenceStartMessage(): String =
        JSONObject().put("event", "sentence_start").toString()

    private fun sentenceEndMessage(): String =
        JSONObject().put("event", "sentence_end").toString()

    private fun taskCanceledMessage(): String =
        JSONObject().put("event", "task_canceled").toString()

    private fun taskFlushedMessage(): String =
        JSONObject().put("event", "task_flushed").toString()

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    @Test fun `tts full handshake streams hex audio and returns result`() = runBlocking {
        val taskStartMsg = StringBuilder()
        val taskContinueMsg = StringBuilder()
        val taskFinishMsg = StringBuilder()

        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
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
        // ★ P0-MAX-1 (§18)：speak() 必须在 taskStarted 之后调 player.play()，
        //   漏掉此调用 → AudioTrack 不出声。验收断言。
        assertTrue(
            "P0-MAX-1: speak() must invoke player.play() at least once before audio write",
            sink.playCount >= 1,
        )

        val startJson = JSONObject(taskStartMsg.toString())
        assertEquals(MiniMaxTtsClient.MINIMAX_TTS_MODEL, startJson.getString("model"))
        // ★ v0.12.0 新增 language_boost
        assertEquals(MiniMaxTtsClient.LANGUAGE_BOOST, startJson.getString("language_boost"))
        val voiceSetting = startJson.getJSONObject("voice_setting")
        assertEquals(MiniMaxTtsClient.MINIMAX_TTS_VOICE_ID, voiceSetting.getString("voice_id"))
        val audioSetting = startJson.getJSONObject("audio_setting")
        assertEquals("pcm", audioSetting.getString("format"))
        assertEquals(MiniMaxTtsClient.SAMPLE_RATE, audioSetting.getInt("sample_rate"))
        assertEquals(1, audioSetting.getInt("channel"))
        // ★ v0.12.0 删除 bitrate 字段
        assertFalse("audio_setting must not contain bitrate for PCM (v0.12.0)", audioSetting.has("bitrate"))

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

    @Test fun `tts task_failed base_resp status_code 1004 maps to TtsAuthFailed`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    webSocket.send(taskFailedMessage(statusCode = 1004, statusMsg = "invalid api key"))
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

    @Test fun `tts task_failed base_resp status_code 2201 maps to TtsUpstream with code 2201`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    webSocket.send(taskFailedMessage(statusCode = 2201, statusMsg = "idle"))
                    webSocket.close(1000, "failed")
                }
            }
        }))
        try {
            client.speak(apiKey = "k", text = "hello")
            fail("expected AppError.TtsUpstream")
        } catch (e: AppError.TtsUpstream) {
            assertEquals(AppError.Code.TTS_UPSTREAM, e.code)
            assertEquals("2201", e.serverErrorCode)
        }
    }

    @Test fun `tts task_failed base_resp status_code 2205 maps to TtsUpstream with code 2205`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    webSocket.send(taskFailedMessage(statusCode = 2205, statusMsg = "queue full"))
                    webSocket.close(1000, "failed")
                }
            }
        }))
        try {
            client.speak(apiKey = "k", text = "hello")
            fail("expected AppError.TtsUpstream")
        } catch (e: AppError.TtsUpstream) {
            assertEquals(AppError.Code.TTS_UPSTREAM, e.code)
            assertEquals("2205", e.serverErrorCode)
        }
    }

    @Test fun `tts sentence_start and sentence_end events are tolerated`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "task_start" -> webSocket.send(taskStartedMessage())
                    "task_continue" -> Thread {
                        try {
                            // 句 1
                            webSocket.send(sentenceStartMessage())
                            webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(1, 2))))
                            webSocket.send(sentenceEndMessage())
                            // 句 2
                            webSocket.send(sentenceStartMessage())
                            webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(3, 4))))
                            webSocket.send(sentenceEndMessage())
                            // 收口
                            webSocket.send(isFinalMessage())
                            Thread.sleep(100)
                        } catch (_: Throwable) {}
                    }.start()
                    "task_finish" -> {
                        webSocket.send(taskFinishedMessage())
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
        val result = client.speak(apiKey = "sk-test", text = "你好，世界。")
        assertEquals(4, sink.byteCount)
        assertTrue("expected total latency > 0", result.totalLatencyMs > 0)
    }

    @Test fun `tts task_canceled and task_flushed events are tolerated`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "task_start" -> webSocket.send(taskStartedMessage())
                    "task_continue" -> Thread {
                        try {
                            webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(1, 2, 3, 4))))
                            webSocket.send(taskCanceledMessage())
                            webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(5, 6, 7, 8))))
                            webSocket.send(taskFlushedMessage())
                            webSocket.send(isFinalMessage())
                            Thread.sleep(100)
                        } catch (_: Throwable) {}
                    }.start()
                    "task_finish" -> {
                        webSocket.send(taskFinishedMessage())
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
        val result = client.speak(apiKey = "sk-test", text = "你好，世界。")
        assertEquals(8, sink.byteCount)
        assertTrue(result.totalLatencyMs > 0)
    }

    @Test fun `tts task_failed without base_resp maps to TtsUpstream with MISSING_BASE_RESP`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    // 极端 case：服务端没填 base_resp（不合法但要兜底）
                    webSocket.send(JSONObject().put("event", "task_failed").toString())
                    webSocket.close(1000, "failed")
                }
            }
        }))
        try {
            client.speak(apiKey = "k", text = "hello")
            fail("expected AppError.TtsUpstream")
        } catch (e: AppError.TtsUpstream) {
            assertEquals(AppError.Code.TTS_UPSTREAM, e.code)
            assertEquals("MISSING_BASE_RESP", e.serverErrorCode)
        }
    }

    // ★ P0-MAX-3 (§18)：首次 speak() 成功后，第二次 speak() 必须可成功。
    //   漏 speaking.set(false) → flag 留 true → 下次 speak() 抛 "TTS already speaking"，
    //   本会话后续所有 TTS 全部失败。
    @Test fun `speak twice in sequence does not throw TTS already speaking`() = runBlocking {
        repeat(2) {
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                    Thread {
                        try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                    }.start()
                }
                override fun onMessage(webSocket: WebSocket, text: String) {
                    when (typeOf(text)) {
                        "task_start" -> webSocket.send(taskStartedMessage())
                        "task_continue" -> Thread {
                            try {
                                webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(1, 2, 3, 4))))
                                webSocket.send(isFinalMessage())
                                Thread.sleep(50)
                            } catch (_: Throwable) {}
                        }.start()
                        "task_finish" -> {
                            webSocket.send(taskFinishedMessage())
                            webSocket.close(1000, "done")
                        }
                    }
                }
            }))
        }
        repeat(2) { i ->
            val result = client.speak(apiKey = "sk-test", text = "第 ${i + 1} 句")
            assertTrue("call #${i + 1} latency must be > 0", result.totalLatencyMs > 0)
        }
    }

    // ★ P0-MAX-3 (§18)：中间失败一次 speak() 后，下一次必须仍可发起（finally 路径覆盖）。
    @Test fun `speak after a failed speak does not throw TTS already speaking`() = runBlocking {
        // 第一次：服务端主动回 task_failed (2201) → finished.completeException 立即触发 catch 分支
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "task_start") {
                    webSocket.send(taskFailedMessage(2201, "soft"))
                    // ★ 关键：server 端必须显式 close 才能让 MockWebServer dispatcher
                    //   在 tearDown() 5s 窗口内 drain；只等 client 端 close frame 不够。
                    webSocket.close(1000, "failed")
                }
            }
        }))
        try {
            client.speak(apiKey = "sk-test", text = "will fail")
            fail("expected TtsUpstream on first speak")
        } catch (e: AppError.TtsUpstream) {
            assertEquals("2201", e.serverErrorCode)
        }

        // 第二次：正常 mock，必须不抛 "TTS already speaking"
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Thread {
                    try { webSocket.send(connectedSuccessMessage()) } catch (_: Throwable) {}
                }.start()
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "task_start" -> webSocket.send(taskStartedMessage())
                    "task_continue" -> Thread {
                        try {
                            webSocket.send(audioDataMessage(bytesToHex(byteArrayOf(9, 9, 9, 9))))
                            webSocket.send(isFinalMessage())
                            Thread.sleep(50)
                        } catch (_: Throwable) {}
                    }.start()
                    "task_finish" -> {
                        webSocket.send(taskFinishedMessage())
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
        val result = client.speak(apiKey = "sk-test", text = "second")
        assertTrue("second speak after timeout must succeed", result.totalLatencyMs > 0)
    }

    // ★ P0-MAX-2 (§18)：defaultClient 必须配 .pingInterval(30s)，否则 v0.12.0 bidi
    //   服务端 120s 主动断会无 ping 兜底；旧自定义 pinger 的 ws.send("") 是 text 帧不合规。
    @Test fun `defaultClient configures rfc6455 ping interval`() {
        val default = MiniMaxTtsClient.defaultClient()
        assertEquals(
            "P0-MAX-2: defaultClient must use MiniMax PING_INTERVAL_MS for RFC 6455 ping frames",
            MiniMaxTtsClient.PING_INTERVAL_MS.toInt(),
            default.pingIntervalMillis,
        )
    }


@Test fun `voice_id model url and audio format match A13`() {
        assertEquals("Cantonese_KindWoman", MiniMaxTtsClient.MINIMAX_TTS_VOICE_ID)
        assertEquals("speech-2.8-hd", MiniMaxTtsClient.MINIMAX_TTS_MODEL)
        assertEquals(
            "wss://api.minimax.cn/ws/v1/t2a_v2_bidi",
            MiniMaxTtsClient.WS_URL,
        )
        assertTrue(
            "WS_URL must end with t2a_v2_bidi (v0.12.0 bidi upgrade)",
            MiniMaxTtsClient.WS_URL.endsWith("t2a_v2_bidi"),
        )
        assertEquals(24_000, MiniMaxTtsClient.SAMPLE_RATE)
        assertEquals("pcm", MiniMaxTtsClient.AUDIO_FORMAT)
        assertEquals("Chinese,Yue", MiniMaxTtsClient.LANGUAGE_BOOST)
        assertEquals(30_000L, MiniMaxTtsClient.PING_INTERVAL_MS)
        assertEquals(120_000L, MiniMaxTtsClient.IDLE_TIMEOUT_MS)
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

    private class RecordingSink : PcmSink {
        @Volatile var byteCount: Int = 0
            private set
        // ★ P0-MAX-1：track play() 调用次数，防止 v0.12.0 漏调 play() 回归
        @Volatile var playCount: Int = 0
            private set

        override fun play() { playCount++ }
        override fun write(bytes: ByteArray) { byteCount += bytes.size }
        override fun drain() = Unit
        override fun release() = Unit
    }
}
