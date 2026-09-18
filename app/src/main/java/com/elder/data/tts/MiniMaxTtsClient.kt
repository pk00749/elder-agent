// 对应 PRD §3.1.9 / §A.13 MiniMax T2A TTS 客户端（v0.7.0 默认 Provider）
//
// 协议（对齐参考 Python 例子）：
//   - 端点：wss://api.minimax.cn/ws/v1/t2a_v2        （v0.7.0 必带 _v2 后缀）
//   - Auth：Authorization: Bearer <API_KEY>          （MiniMax TTS 独立 Key，
//                                                    §5.11 tts_minimax_api_key_enc）
//   - 建连：服务端先发 {"event":"connected_success"}
//   - 配任务：客户端发 {"event":"task_start", model, voice_setting, audio_setting}
//            服务端回 {"event":"task_started"}
//   - 推文本：客户端发 {"event":"task_continue", text}
//   - 收音频：服务端连续回 {"data":{"audio":"<hex>"}}  音频块；hex 解码后写 PcmSink
//   - 收口：服务端发 {"is_final":true}（顶层字段，非嵌套）
//   - 关连：客户端发 {"event":"task_finish"} 后关 WebSocket
//
// 历史 WebSocket 占位（session.start / text.chunk / audio.delta / session.done /
// session.finish）已废弃；字段名按 Python 协议族敲定。
//
// 音频输出格式：audio_setting.format = "pcm" + sample_rate = 24000 + channel = 1
//   与 AndroidPcmSink（24kHz / mono / 16-bit）匹配；如 MiniMax 服务端拒绝此组合
//   再考虑加 MediaCodec 解码 mp3（Python 例子的默认配置）。
//
// voice_id 硬编码 Cantonese_KindWoman（§A.13.1）；待 0.7.0 真接后校验有效性。
package com.elder.android.data.tts

import com.elder.android.error.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * MiniMax T2A 实现（§A.13）。
 * API Key 走独立 [com.elder.android.data.crypto.ApiKeyCipher.KEY_TTS_MINIMAX_API_KEY_ENC]；
 * 由 ServiceLocator.ttsClient() 在 Provider = minimax 时返回本实例。
 */
class MiniMaxTtsClient(
    private val sinkFactory: () -> PcmSink = { AndroidPcmSink.create() },
    private val client: OkHttpClient = defaultClient(),
    private val wsUrl: String = WS_URL,
) : TtsClient {

    private val speaking = AtomicBoolean(false)
    @Volatile private var current: WebSocket? = null

    override suspend fun speak(apiKey: String, text: String): TtsResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.TtsAuthFailed()
        if (!speaking.compareAndSet(false, true)) {
            throw AppError.TtsUpstream(IOException("TTS already speaking"))
        }

        val startedAt = System.currentTimeMillis()
        val firstAudioAt = AtomicLong(0L)
        val connected = CompletableDeferred<Unit>()
        val taskStarted = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val player = sinkFactory()
        var protocolError: AppError? = null

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // 服务端在升级后会主动 push {"event":"connected_success"}；
                // 这里不做主动 send，等 connected 信号。
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)
                    when (event.optString("event")) {
                        "connected_success" -> completeValue(connected, Unit)
                        "task_started" -> completeValue(taskStarted, Unit)
                        "task_finished" -> {
                            // 服务端确认 task_finish 已收；正常完成。
                            completeValue(finished, Unit)
                        }
                        "task_failed" -> {
                            val err = mapStreamError(event.optJSONObject("error"))
                            protocolError = err
                            completeException(finished, err)
                            completeException(connected, err)
                            completeException(taskStarted, err)
                            runCatching { webSocket.close(WS_CLOSE_NORMAL, "task_failed") }
                        }
                    }
                    val isFinal = event.optBoolean("is_final", false)
                    if (isFinal) completeValue(finished, Unit)

                    val data = event.optJSONObject("data")
                    val audio = data?.optString("audio")
                    if (!audio.isNullOrEmpty()) {
                        val bytes = decodeHexOrNull(audio)
                        if (bytes == null) {
                            val err = AppError.TtsUpstream(
                                IOException("MiniMax T2A audio payload is not valid hex"),
                            )
                            protocolError = err
                            completeException(finished, err)
                            runCatching { webSocket.close(WS_CLOSE_NORMAL, "bad_audio") }
                            return
                        }
                        if (firstAudioAt.compareAndSet(0L, System.currentTimeMillis())) {
                            // 第一次收到音频即开始播放。
                            runCatching { player.play() }
                        }
                        runCatching { player.write(bytes) }
                            .onFailure {
                                val err = AppError.TtsUpstream(
                                    IOException("Failed to write PCM chunk: ${it.message}", it),
                                )
                                protocolError = err
                                completeException(finished, err)
                                runCatching { webSocket.close(WS_CLOSE_NORMAL, "pcm_write_failed") }
                            }
                    }
                } catch (_: Throwable) {
                    // 忽略单条事件解析错误；最终由 is_final / task_finished / onFailure 收口。
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val err = mapFailure(t, response)
                completeException(connected, err)
                completeException(taskStarted, err)
                completeException(finished, err)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!finished.isCompleted && code != WS_CLOSE_NORMAL) {
                    completeException(
                        finished,
                        AppError.TtsUpstream(serverErrorCode = code.toString(), serverErrorMessage = reason),
                    )
                }
            }
        })
        current = ws

        try {
            // 1. 等 connected_success（服务端推送）
            withTimeout(SESSION_TIMEOUT_MS) { connected.await() }

            // 2. 发 task_start 配任务
            if (!ws.send(buildTaskStartMessage())) {
                throw AppError.TtsUpstream(IOException("Failed to send MiniMax task_start"))
            }

            // 3. 等 task_started
            withTimeout(SESSION_TIMEOUT_MS) { taskStarted.await() }

            // 4. 发 task_continue 推文本
            if (!ws.send(buildTaskContinueMessage(text))) {
                throw AppError.TtsUpstream(IOException("Failed to send MiniMax task_continue"))
            }

            // 5. 等 is_final / task_finished；服务端会持续回流 audio.data
            withTimeout(RESPONSE_TIMEOUT_MS) { finished.await() }

            // 6. 发 task_finish 关任务，再 close
            runCatching { ws.send(buildTaskFinishMessage()) }
            runCatching { ws.close(WS_CLOSE_NORMAL, "done") }

            player.drain()
            val firstAudioDelay = firstAudioAt.get().let {
                if (it == 0L) -1L else it - startedAt
            }
            TtsResult(
                firstAudioDelayMs = firstAudioDelay,
                totalLatencyMs = System.currentTimeMillis() - startedAt,
            )
        } catch (e: TimeoutCancellationException) {
            throw AppError.TtsUpstream(IOException("MiniMax T2A response timeout"))
        } catch (e: AppError) {
            throw e
        } catch (t: Throwable) {
            throw protocolError ?: AppError.TtsUpstream(t)
        } finally {
            runCatching { ws.close(WS_CLOSE_NORMAL, "done") }
            current = null
            player.release()
            speaking.set(false)
        }
    }

    override fun stop() {
        runCatching { current?.close(WS_CLOSE_NORMAL, "stop") }
        current = null
    }

    private fun buildTaskStartMessage(): String =
        JSONObject().apply {
            put("event", "task_start")
            put("model", MINIMAX_TTS_MODEL)
            put("voice_setting", JSONObject().apply {
                put("voice_id", MINIMAX_TTS_VOICE_ID)
                put("speed", 1)
                put("vol", 1)
                put("pitch", 0)
                put("english_normalization", false)
            })
            put("audio_setting", JSONObject().apply {
                put("sample_rate", SAMPLE_RATE)
                put("bitrate", BITRATE)
                put("format", AUDIO_FORMAT)
                put("channel", 1)
            })
        }.toString()

    private fun buildTaskContinueMessage(text: String): String =
        JSONObject().apply {
            put("event", "task_continue")
            put("text", text)
        }.toString()

    private fun buildTaskFinishMessage(): String =
        JSONObject().apply { put("event", "task_finish") }.toString()

    private fun decodeHexOrNull(hex: String): ByteArray? {
        // 容错处理：忽略空白 / 大小写；非 hex 字符返回 null。
        val cleaned = hex.replace("\\s".toRegex(), "")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
        return try {
            val out = ByteArray(cleaned.length / 2)
            for (i in out.indices) {
                val byte = cleaned.substring(i * 2, i * 2 + 2).toInt(16)
                out[i] = byte.toByte()
            }
            out
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun mapStreamError(error: JSONObject?): AppError {
        val code = error?.optString("code").orEmpty()
        val message = error?.optString("message").orEmpty()
        val normalized = "$code $message".lowercase()
        return when {
            normalized.contains("auth") ||
                normalized.contains("api key") ||
                normalized.contains("apikey") ||
                "401" in normalized ||
                "403" in normalized -> AppError.TtsAuthFailed()
            normalized.contains("throttl") || normalized.contains("rate limit") || "429" in normalized ->
                AppError.TtsUpstream(serverErrorCode = code.ifBlank { null },
                    serverErrorMessage = message.ifBlank { null })
            normalized.contains("invalid") || normalized.contains("bad request") ->
                AppError.TtsUpstream(serverErrorCode = code.ifBlank { null },
                    serverErrorMessage = message.ifBlank { null })
            else -> AppError.TtsUpstream(
                serverErrorCode = code.ifBlank { null },
                serverErrorMessage = message.ifBlank { null },
            )
        }
    }

    private fun mapFailure(t: Throwable, response: Response?): AppError {
        val statusCode = response?.code
        return when {
            statusCode == 401 || statusCode == 403 -> AppError.TtsAuthFailed(t)
            statusCode == 429 -> AppError.TtsUpstream(t, serverErrorCode = "429")
            statusCode != null && statusCode in 400..499 ->
                AppError.TtsUpstream(t, serverErrorCode = statusCode.toString())
            else -> AppError.TtsUpstream(t)
        }
    }

    private fun <T> completeValue(deferred: CompletableDeferred<T>, value: T) {
        if (!deferred.isCompleted) deferred.complete(value)
    }

    private fun <T> completeException(deferred: CompletableDeferred<T>, error: AppError) {
        if (!deferred.isCompleted) deferred.completeExceptionally(error)
    }

    companion object {
        const val MINIMAX_TTS_PROVIDER = "minimax"
        const val MINIMAX_TTS_MODEL = "speech-2.8-hd"          // §A.13.1，与 Python 例子一致
        const val MINIMAX_TTS_VOICE_ID = "Cantonese_KindWoman" // §A.13.1 硬编码；待真 Key 校验
        const val WS_URL = "wss://api.minimax.cn/ws/v1/t2a_v2" // 必须带 _v2 后缀
        const val SAMPLE_RATE = 24_000                          // 与 AndroidPcmSink 一致
        const val BITRATE = 128_000
        const val AUDIO_FORMAT = "pcm"                          // 24kHz / mono / 16-bit

        private const val SESSION_TIMEOUT_MS = 10_000L
        private const val RESPONSE_TIMEOUT_MS = 30_000L
        private const val WS_CLOSE_NORMAL = 1000

        fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            return OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }
    }
}
