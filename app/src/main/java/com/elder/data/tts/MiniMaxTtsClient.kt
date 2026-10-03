// 对应 PRD §3.1.9 / §A.13 MiniMax T2A TTS 客户端（v0.12.0 切到双向 bidi）
//
// 协议（MiniMax `t2a_v2_bidi` WebSocket，详见 docs/v0.12.0-minimax-tts-bidi.md）：
//   - 端点：wss://api.minimax.cn/ws/v1/t2a_v2_bidi
//   - Auth：Authorization: Bearer <API_KEY>           （MiniMax TTS 独立 Key，
//                                                     §5.11 tts_minimax_api_key_enc）
//   - 建连：服务端先发 {"event":"connected_success"}
//   - 配任务：客户端发 {"event":"task_start", model, language_boost,
//                      voice_setting, audio_setting}
//            服务端回 {"event":"task_started"}
//   - 推文本：客户端发 {"event":"task_continue", text}        —— 可按任意粒度逐字/逐 token；
//                                                              服务端自动攒句
//   - 收音频：服务端连续回 {"event":"task_continued","data":{"audio":"<hex>"},
//                          "is_final":false/true}          ← hex 解码后写 PcmSink
//   - 句边界：服务端回 {"event":"sentence_start"} / {"event":"sentence_end"}
//   - 打断：客户端发 {"event":"task_cancel"} → 服务端回 {"event":"task_canceled"}
//   - 催出：客户端发 {"event":"task_flush"} → 服务端回 {"event":"task_flushed"}
//   - 关连：客户端发 {"event":"task_finish"} → 服务端合成残留 → 回 {"event":"task_finished"}
//
// 音频输出格式：audio_setting.format = "pcm" + sample_rate = 24000 + channel = 1
//   与 AndroidPcmSink（24kHz / mono / 16-bit）匹配；
//   PCM 无压缩故 audio_setting 不含 bitrate 字段（文档明示「仅对 mp3 生效」）。
//
// voice_id 硬编码 Cantonese_KindWoman（§A.13.1 系统音色第 64 行确认：善良女声/粤语）；
// language_boost = "Chinese,Yue" 强化粤语韵律（§A.13.1 新增；8 模型 + 40 语言场景 合法）。
//
// 历史实现（v0.7.0）走单向 /ws/v1/t2a_v2，老逻辑已废弃。
package com.elder.android.data.tts

import com.elder.android.error.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Response as OkResponse
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * MiniMax T2A 双向 bidi 实现（§A.13，v0.12.0）。
 *
 * 端点固定为 `wss://api.minimax.cn/ws/v1/t2a_v2_bidi`；服务端攒句 +
 * 老人打断 + 尾音不丢三件能力在 bidi endpoint 下默认开启。
 *
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
            override fun onOpen(webSocket: WebSocket, response: OkResponse) {
                // 服务端在升级后会主动 push {"event":"connected_success"}；不发主动 send
                // ★ P0-MAX-2：保活由 OkHttp.pingInterval 发 RFC 6455 ping 帧，不再应用层 send("")。
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val event = JSONObject(text)
                    when (event.optString("event")) {
                        "connected_success" -> completeValue(connected, Unit)
                        "task_started" -> completeValue(taskStarted, Unit)
                        "sentence_start" -> {
                            // §A.13 bidi 句开始；仅 debug 日志
                            android.util.Log.d(
                                "MiniMaxTts",
                                "sentence_start trace=${event.optString("trace_id")}",
                            )
                        }
                        "sentence_end" -> {
                            android.util.Log.d(
                                "MiniMaxTts",
                                "sentence_end trace=${event.optString("trace_id")}",
                            )
                        }
                        "task_canceled" -> {
                            android.util.Log.d("MiniMaxTts", "task_canceled")
                        }
                        "task_flushed" -> {
                            android.util.Log.d("MiniMaxTts", "task_flushed")
                        }
                        "task_finished" -> {
                            // 服务端确认 task_finish 已收；正常完成。
                            completeValue(finished, Unit)
                        }
                        "task_failed" -> {
                            val err = mapBaseRespError(event.optJSONObject("base_resp"))
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
                                serverErrorCode = "invalid_hex",
                                serverErrorMessage = "MiniMax T2A audio payload is not valid hex",
                            )
                            protocolError = err
                            completeException(finished, err)
                            runCatching { webSocket.close(WS_CLOSE_NORMAL, "bad_audio") }
                            return
                        }
                        if (firstAudioAt.compareAndSet(0L, System.currentTimeMillis())) {
                            // first audio latency recorded
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

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: OkResponse?) {
                // ★ P0-MAX-2：OkHttp 客户端管 ping，不再调 stopPinger()
                val err = mapFailure(t, response)
                completeException(connected, err)
                completeException(taskStarted, err)
                completeException(finished, err)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                // ★ P0-MAX-2：OkHttp 客户端管 ping
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

            // ★ P0-MAX-1 (§18)：服务端准备好后第一时间启动播放，让 write() 进来的 PCM 能立即出声。
            //   遗漏此调用 → AudioTrack 停在 STATE_INITIALIZED，所有 PCM 躺在 buffer，
            //   直到 player.drain() 才被清空 → MiniMax 用户 100% 静音。
            //   对齐 QwenTtsClient.kt:103 的 player.play() 位置。
            player.play()

            // 4. 发 task_continue 推文本
            if (!ws.send(buildTaskContinueMessage(text))) {
                throw AppError.TtsUpstream(IOException("Failed to send MiniMax task_continue"))
            }

            // 5. 等 is_final / task_finished；服务端会持续回流 audio.data
            withTimeout(RESPONSE_TIMEOUT_MS) { finished.await() }

            // 6. 发 task_finish 关任务；bidi 会先送残留再 task_finished（§A.13.2）
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
            throw AppError.TtsUpstream(
                serverErrorCode = "TIMEOUT",
                serverErrorMessage = "MiniMax T2A response timeout (${RESPONSE_TIMEOUT_MS}ms)",
            )
        } catch (e: AppError) {
            throw e
        } catch (t: Throwable) {
            throw protocolError ?: AppError.TtsUpstream(t)
        } finally {
            // ★ P0-MAX-2：OkHttp 客户端管 ping；连接关闭由 ws.close / onClosed 走完
            runCatching { ws.close(WS_CLOSE_NORMAL, "done") }
            current = null
            player.release()
            // ★ P0-MAX-3 (§18)：重置 speaking flag，对齐 QwenTtsClient.kt:123。
            //   漏此调用 → speak() 走 finally 后 flag 留 true → 下次 speak() 抛 "TTS already speaking"，
            //   本会话后续所有 TTS 全部失败，老人听不到任何回复。
            speaking.set(false)
        }
    }

    override fun stop() {
        // ★ P0-MAX-2：OkHttp 客户端管 ping，不再调 stopPinger()
        runCatching { current?.close(WS_CLOSE_NORMAL, "stop") }
        current = null
        // ★ P0-MAX-3 (§18)：用户打断后重置 speaking flag，对齐 QwenTtsClient 的隐式契约。
        //   InterviewViewModel.cancel() 路径：当前 state = stopped。后续 LLM 出 state。
        //   漏此调用 → 本会话剩余所有 TTS 全部失败。
        speaking.set(false)
    }

    private fun buildTaskStartMessage(): String =
        JSONObject().apply {
            put("event", "task_start")
            put("model", MINIMAX_TTS_MODEL)
            put("language_boost", LANGUAGE_BOOST)
            put("voice_setting", JSONObject().apply {
                put("voice_id", MINIMAX_TTS_VOICE_ID)
                put("speed", 1)
                put("vol", 1)
                put("pitch", 0)
                put("english_normalization", false)
            })
            put("audio_setting", JSONObject().apply {
                put("sample_rate", SAMPLE_RATE)
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

    /**
     * 错误码精确映射（v0.12.0 §A.13 §3.4）：读 `event.base_resp.status_code` int。
     *
     * 文档来源：https://platform.minimaxi.com/docs/api-reference/speech-t2a-websocket-bidi.md
     * 旧实现读 `error.code/message` 字符串关键字 → 100% 拿到 null，因为文档**没有** error 字段。
     */
    private fun mapBaseRespError(baseResp: JSONObject?): AppError {
        if (baseResp == null) {
            return AppError.TtsUpstream(
                serverErrorCode = "MISSING_BASE_RESP",
                serverErrorMessage = "task_failed event without base_resp",
            )
        }
        val code = baseResp.optInt("status_code", -1)
        val msg = baseResp.optString("status_msg").orEmpty()
        return when (code) {
            1004 -> AppError.TtsAuthFailed()
            2204 -> AppError.TtsUpstream(
                serverErrorCode = "2204",
                serverErrorMessage = msg.ifBlank { "task_continue text > 10,000 chars; skipped (session kept)" },
            )
            2205 -> AppError.TtsUpstream(
                serverErrorCode = "2205",
                serverErrorMessage = msg.ifBlank { "queue overflow; resend later (session kept)" },
            )
            2206 -> AppError.TtsUpstream(
                serverErrorCode = "2206",
                serverErrorMessage = msg.ifBlank { "event order illegal (e.g. duplicate task_start)" },
            )
            1000, 1001, 1002, 1039, 1042, 2013, 2201, 2202 -> AppError.TtsUpstream(
                serverErrorCode = code.toString(),
                serverErrorMessage = msg.ifBlank { null },
            )
            else -> AppError.TtsUpstream(
                serverErrorCode = code.takeIf { it >= 0 }?.toString() ?: "UNKNOWN",
                serverErrorMessage = msg.ifBlank { null },
            )
        }
    }

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

    private fun mapFailure(t: Throwable, response: OkResponse?): AppError {
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
        const val MINIMAX_TTS_MODEL = "speech-2.8-hd"               // 8 模型 enum 内合法
        const val MINIMAX_TTS_VOICE_ID = "Cantonese_KindWoman"      // 系统音色第 64 行
        const val WS_URL = "wss://api.minimax.cn/ws/v1/t2a_v2_bidi" // ★ v0.12.0 切 bidi
        const val SAMPLE_RATE = 24_000                               // 6 档 enum 内合法
        const val AUDIO_FORMAT = "pcm"                               // 7 档 enum 内合法
        const val LANGUAGE_BOOST = "Chinese,Yue"                     // 粤语场景方阵
        const val PING_INTERVAL_MS = 30_000L                          // 文档明示 client ping
        const val IDLE_TIMEOUT_MS = 120_000L                          // 文档明示服务端断连阈值

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
                // ★ P0-MAX-2 (§18)：RFC 6455 ping/pong 帧保活。
                //   旧自定义 pinger 用 ws.send("") 发空 text 帧 → 服务端回 task_failed(2206)
                //   OkHttp 4.x 自动用 PingMessage 帧发 ping → 服务端 pong 刷新活跃时间。
                //   MiniMax 120s 主动断，30s 间隔保证心跳 < 120s。
                .pingInterval(MiniMaxTtsClient.PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(logging)
                .build()
        }
    }
}
