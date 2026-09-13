// 对应 PRD §3.1.9 + §10.4 阿里云百炼 ASR 客户端（v3.0 MVP 唯一上游）
//
// Realtime WebSocket 协议（对齐 scripts/realtime_quickstart.py）：
//   - 端点：wss://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api-ws/v1/realtime?model=...
//   - Auth：Authorization: Bearer <API_KEY>
//   - 录音中：input_audio_buffer.append × N -> transcription.delta 实时刷新
//   - 停止后：input_audio_buffer.commit -> transcription.completed 最终收口
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

class AsrApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val wsUrl: String = WS_URL,
) {
    suspend fun openSession(
        apiKey: String,
        onPartial: (String) -> Unit = {},
    ): RealtimeAsrSession = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.AsrAuthFailed()
        createSession(apiKey, onPartial)
    }

    suspend fun transcribe(apiKey: String, audioFile: File): AsrResult {
        val session = openSession(apiKey)
        return try {
            FileInputStream(audioFile).use { input ->
                val buffer = ByteArray(AUDIO_CHUNK_BYTES)
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

    private suspend fun createSession(
        apiKey: String,
        onPartial: (String) -> Unit,
    ): RealtimeAsrSession {
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val sessionReady = CompletableDeferred<Unit>()
        val transcript = CompletableDeferred<String>()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)
                    when (event.optString("type")) {
                        "session.updated" -> {
                            completeValue(sessionReady, Unit)
                        }
                        "conversation.item.input_audio_transcription.delta" -> {
                            if (!transcript.isCompleted) {
                                val partial = event.optString("text") + event.optString("stash")
                                if (partial.isNotBlank()) onPartial(partial)
                            }
                        }
                        "conversation.item.input_audio_transcription.completed" -> {
                            if (!transcript.isCompleted) {
                                transcript.complete(event.optString("transcript"))
                            }
                        }
                        "conversation.item.input_audio_transcription.failed" -> {
                            completeException(
                                transcript,
                                mapRealtimeFailure(event.optJSONObject("error")),
                            )
                        }
                        "error" -> {
                            val error = mapRealtimeFailure(event.optJSONObject("error"))
                            completeException(sessionReady, error)
                            completeException(transcript, error)
                            webSocket.close(WS_CLOSE_NORMAL, "failed")
                        }
                    }
                } catch (_: Throwable) {
                    // 忽略单条事件解析错误；最终由 completed/failed/onFailure 收口。
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val error = mapHandshakeFailure(t, response)
                completeException(sessionReady, error)
                completeException(transcript, error)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val error = AppError.AsrUpstream(IOException("Bailian Realtime closed before transcript"))
                completeException(sessionReady, error)
                completeException(transcript, error)
            }
        })

        return try {
            if (!ws.send(buildSessionUpdateMessage())) {
                throw AppError.AsrUpstream(IOException("Failed to send Realtime session.update"))
            }
            withTimeoutOrNull(SESSION_TIMEOUT_MS) { sessionReady.await() }
                ?: throw AppError.AsrUpstream(IOException("Bailian Realtime session.updated timeout"))

            RealtimeAsrSession(ws, transcript)
        } catch (t: Throwable) {
            ws.close(WS_CLOSE_NORMAL, "failed")
            throw t
        }
    }

    inner class RealtimeAsrSession internal constructor(
        private val socket: WebSocket,
        private val transcript: CompletableDeferred<String>,
    ) {
        @Volatile private var closed: Boolean = false

        fun appendAudio(audio: ByteArray) {
            if (closed || transcript.isCompleted) return
            if (!socket.send(buildAppendMessage(audio))) {
                completeException(
                    transcript,
                    AppError.AsrUpstream(IOException("Failed to send Realtime audio frame")),
                )
                closed = true
            }
        }

        suspend fun finish(): AsrResult {
            if (!socket.send(buildCommitMessage())) {
                throw AppError.AsrUpstream(IOException("Failed to send Realtime input_audio_buffer.commit"))
            }
            val text = withTimeoutOrNull(TRANSCRIPT_TIMEOUT_MS) {
                transcript.await()
            } ?: throw AppError.AsrUpstream(IOException("Bailian Realtime transcript timeout"))
            if (text.isBlank()) throw AppError.AsrEmptyTranscript()
            return AsrResult(text = text.trim(), confidence = null, httpStatus = 200)
        }

        fun close() {
            closed = true
            socket.close(WS_CLOSE_NORMAL, "done")
        }
    }

    private fun buildSessionUpdateMessage(): String =
        JSONObject().apply {
            put("event_id", newEventId())
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("modalities", JSONArray().apply {
                    put("text")
                })
                put("turn_detection", JSONObject.NULL)
            })
        }.toString()

    private fun buildAppendMessage(audio: ByteArray): String =
        JSONObject().apply {
            put("event_id", newEventId())
            put("type", "input_audio_buffer.append")
            put("audio", Base64.getEncoder().encodeToString(audio))
        }.toString()

    private fun buildCommitMessage(): String =
        JSONObject().apply {
            put("event_id", newEventId())
            put("type", "input_audio_buffer.commit")
        }.toString()

    private fun mapRealtimeFailure(error: JSONObject?): AppError {
        val type = error?.optString("type").orEmpty()
        val code = error?.optString("code").orEmpty()
        val message = error?.optString("message").orEmpty()
        val normalized = "$type $code $message".lowercase()

        return when {
            normalized.contains("auth") ||
                normalized.contains("apikey") ||
                normalized.contains("api key") -> AppError.AsrAuthFailed()
            normalized.contains("throttl") || normalized.contains("rate limit") -> AppError.AsrRateLimited()
            normalized.contains("invalid") ||
                normalized.contains("bad request") -> AppError.AsrBadRequest()
            else -> AppError.AsrUpstream(
                serverErrorCode = code.ifBlank { null },
                serverErrorMessage = message.ifBlank { null },
            )
        }
    }

    private fun mapHandshakeFailure(t: Throwable, response: Response?): AppError {
        val statusCode = response?.code
        return when {
            statusCode == 401 || statusCode == 403 -> AppError.AsrAuthFailed(t)
            statusCode == 429 -> AppError.AsrRateLimited(t)
            statusCode != null && statusCode in 400..499 -> AppError.AsrBadRequest(t)
            else -> AppError.AsrUpstream(t)
        }
    }

    private fun <T> completeValue(deferred: CompletableDeferred<T>, value: T) {
        if (!deferred.isCompleted) deferred.complete(value)
    }

    private fun <T> completeException(deferred: CompletableDeferred<T>, error: AppError) {
        if (!deferred.isCompleted) deferred.completeExceptionally(error)
    }

    private fun newEventId(): String = "event_${UUID.randomUUID()}"

    companion object {
        const val BAILIAN_WORKSPACE_ID = "llm-svrk4hi977f8t2fe"
        const val BAILIAN_REGION = "cn-beijing"
        const val BAILIAN_PROVIDER = "bailian"
        const val BAILIAN_MODEL = "qwen-audio-3.0-realtime-plus"
        val WS_URL = "wss://$BAILIAN_WORKSPACE_ID.$BAILIAN_REGION.maas.aliyuncs.com/api-ws/v1/realtime?model=$BAILIAN_MODEL"

        private const val SESSION_TIMEOUT_MS = 10_000L
        private const val TRANSCRIPT_TIMEOUT_MS = 30_000L
        private const val AUDIO_CHUNK_BYTES = 3200
        private const val WS_CLOSE_NORMAL = 1000

        fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }
    }
}

data class AsrResult(
    val text: String,
    val confidence: Float?,
    val httpStatus: Int,
)
