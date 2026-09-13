// §3.1.9 + §A.8 阿里云百炼 Realtime ASR 客户端测试：WebSocket 协议 + 错误映射
package com.elder.android.data.asr

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        sample = File.createTempFile("asr_test", ".wav")
        sample.writeBytes(ByteArray(7200) { (it and 0xff).toByte() })
    }

    @After fun tearDown() {
        server.shutdown()
        sample.delete()
    }

    private fun wsUrl(): String =
        server.url("/realtime").toString().replaceFirst("http://", "ws://")

    private fun typeOf(text: String): String =
        JSONObject(text).optString("type")

    private fun sessionUpdatedMessage(): String =
        JSONObject().put("type", "session.updated").put("session", JSONObject()).toString()

    private fun transcriptCompletedMessage(transcript: String): String =
        JSONObject().apply {
            put("type", "conversation.item.input_audio_transcription.completed")
            put("transcript", transcript)
            put("item_id", "item_x")
            put("content_index", 0)
        }.toString()

    private fun transcriptDeltaMessage(partial: String): String =
        JSONObject().apply {
            put("type", "conversation.item.input_audio_transcription.delta")
            put("text", "")
            put("stash", partial)
            put("item_id", "item_x")
            put("content_index", 0)
        }.toString()

    private fun realtimeErrorMessage(code: String, message: String): String =
        JSONObject().apply {
            put("type", "error")
            put("error", JSONObject().apply {
                put("type", "invalid_request_error")
                put("code", code)
                put("message", message)
            })
        }.toString()

    private fun enqueueRealtimeError(code: String, message: String) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (typeOf(text) == "session.update") {
                    webSocket.send(realtimeErrorMessage(code, message))
                    webSocket.close(1000, "failed")
                }
            }
        }))
    }

    @Test fun `waits for session updated then sends base64 audio and waits for completed transcript`() = runBlocking {
        val sessionUpdated = AtomicBoolean(false)
        val audioBeforeSessionUpdated = AtomicBoolean(false)
        val receivedAudio = ByteArrayOutputStream()
        val sessionUpdate = StringBuilder()
        val commit = AtomicBoolean(false)
        val partials = CopyOnWriteArrayList<String>()

        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "session.update" -> {
                        sessionUpdate.append(text)
                        Thread {
                            Thread.sleep(200)
                            sessionUpdated.set(true)
                            webSocket.send(sessionUpdatedMessage())
                        }.start()
                    }
                    "input_audio_buffer.append" -> {
                        if (!sessionUpdated.get()) audioBeforeSessionUpdated.set(true)
                        val audio = JSONObject(text).getString("audio")
                        receivedAudio.write(Base64.getDecoder().decode(audio))
                        webSocket.send(transcriptDeltaMessage("今天天气"))
                    }
                    "input_audio_buffer.commit" -> {
                        commit.set(true)
                        webSocket.send(transcriptCompletedMessage("今天天气很好，我去公园散步了。"))
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))

        val result = client.openSession("sk-bailian-test") { partials.add(it) }.let { session ->
            try {
                FileInputStream(sample).use { input ->
                    val buffer = ByteArray(3200)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        session.appendAudio(buffer.copyOf(read))
                    }
                }
                session.finish()
            } finally {
                session.close()
            }
        }

        assertEquals("今天天气很好，我去公园散步了。", result.text)
        assertTrue("expected realtime partial callback", partials.contains("今天天气"))
        assertFalse("客户端在 session.updated 前发送了音频", audioBeforeSessionUpdated.get())
        assertTrue("未发送 input_audio_buffer.commit", commit.get())
        assertTrue(receivedAudio.toByteArray().contentEquals(sample.readBytes()))

        val updateJson = JSONObject(sessionUpdate.toString())
        val session = updateJson.getJSONObject("session")
        assertEquals("text", session.getJSONArray("modalities").getString(0))
        assertTrue(session.isNull("turn_detection"))
    }

    @Test fun `realtime invalid request maps to ASR_BAD_REQUEST`() = runBlocking {
        enqueueRealtimeError(
            code = "invalid_value",
            message = "turn_detection type is invalid",
        )
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrBadRequest")
        } catch (e: AppError.AsrBadRequest) {
            assertEquals(AppError.Code.ASR_BAD_REQUEST, e.code)
        }
    }

    @Test fun `websocket handshake 401 maps to ASR_AUTH_FAILED`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        try {
            client.transcribe(apiKey = "expired", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
    }

    @Test fun `empty completed transcript maps to ASR_EMPTY_TRANSCRIPT`() = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                when (typeOf(text)) {
                    "session.update" -> webSocket.send(sessionUpdatedMessage())
                    "input_audio_buffer.commit" -> {
                        webSocket.send(transcriptCompletedMessage(""))
                        webSocket.close(1000, "done")
                    }
                }
            }
        }))
        try {
            client.transcribe(apiKey = "k", audioFile = sample)
            fail("expected AppError.AsrEmptyTranscript")
        } catch (e: AppError.AsrEmptyTranscript) {
            assertEquals(AppError.Code.ASR_EMPTY_TRANSCRIPT, e.code)
        }
    }

    @Test fun `blank apiKey throws ASR_AUTH_FAILED before WS connect`() = runBlocking {
        try {
            client.transcribe(apiKey = "", audioFile = sample)
            fail("expected AppError.AsrAuthFailed")
        } catch (e: AppError.AsrAuthFailed) {
            assertEquals(AppError.Code.ASR_AUTH_FAILED, e.code)
        }
    }

    @Test fun `WS_URL and model match realtime quickstart protocol`() {
        assertEquals(
            "wss://llm-svrk4hi977f8t2fe.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen-audio-3.0-realtime-plus",
            AsrApiClient.WS_URL,
        )
        assertEquals("llm-svrk4hi977f8t2fe", AsrApiClient.BAILIAN_WORKSPACE_ID)
        assertEquals("cn-beijing", AsrApiClient.BAILIAN_REGION)
        assertEquals("qwen-audio-3.0-realtime-plus", AsrApiClient.BAILIAN_MODEL)
    }
}
