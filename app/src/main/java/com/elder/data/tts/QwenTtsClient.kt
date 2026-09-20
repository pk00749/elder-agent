// PRD 0.5.0 / §A.11：qwen3-tts-flash-realtime + Kiki，返回 24kHz mono 16-bit PCM。
// v0.7.0 修订：PcmSink / AndroidPcmSink 下沉到顶层 PcmSink.kt 与 MiniMaxTtsClient 共用。
package com.elder.android.data.tts

import android.util.Base64
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtime
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeAudioFormat
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeCallback
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeConfig
import com.alibaba.dashscope.audio.qwen_tts_realtime.QwenTtsRealtimeParam
import com.elder.android.error.AppError
import com.google.gson.JsonObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class QwenTtsClient internal constructor(
    private val sinkFactory: () -> PcmSink,
    private val eventObserver: (String) -> Unit = {},
) : TtsClient {
    constructor() : this({ AndroidPcmSink.create() })

    private val speaking = AtomicBoolean(false)
    @Volatile private var current: QwenTtsRealtime? = null

    override suspend fun speak(apiKey: String, text: String): TtsResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.TtsAuthFailed()
        if (!speaking.compareAndSet(false, true)) throw AppError.TtsUpstream(IOException("TTS already speaking"))

        val startedAt = System.currentTimeMillis()
        val done = CompletableDeferred<Unit>()
        val player = sinkFactory()
        val callback = object : QwenTtsRealtimeCallback() {
            override fun onOpen() {
                eventObserver("open")
            }

            override fun onEvent(response: JsonObject) {
                eventObserver(response.get("type")?.asString.orEmpty())
                when (response.get("type")?.asString) {
                    "response.audio.delta" -> {
                        val encoded = response.get("delta")?.asString ?: return
                        runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                            .onSuccess(player::write)
                            .onFailure { done.completeExceptionally(AppError.TtsUpstream(it)) }
                    }
                    "response.done" -> done.complete(Unit)
                    "error", "session.failed" -> {
                        val error = response.getAsJsonObject("error")
                        done.completeExceptionally(
                            AppError.TtsUpstream(
                                serverErrorCode = error?.get("code")?.asString,
                                serverErrorMessage = error?.get("message")?.asString,
                            )
                        )
                    }
                }
            }

            override fun onClose(closeStatus: Int, closeMsg: String) {
                eventObserver("close:$closeStatus")
                if (!done.isCompleted && closeStatus != NORMAL_CLOSE) {
                    done.completeExceptionally(
                        AppError.TtsUpstream(serverErrorCode = closeStatus.toString(), serverErrorMessage = closeMsg)
                    )
                }
            }
        }

        val realtime = try {
            ObservedQwenTtsRealtime(
                onFailure = { error -> done.completeExceptionally(mapRealtimeFailure(error)) },
                QwenTtsRealtimeParam.builder()
                    .model(MODEL)
                    .apikey(apiKey)
                    .url(REALTIME_ENDPOINT)
                    .build(),
                callback,
            )
        } catch (t: Throwable) {
            player.release()
            speaking.set(false)
            throw AppError.TtsUpstream(t)
        }
        current = realtime

        try {
            realtime.connect()
            realtime.updateSession(
                QwenTtsRealtimeConfig.builder()
                    .voice(VOICE)
                    .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                    .mode("server_commit")
                    .languageType("Chinese")
                    .build()
            )
            player.play()
            realtime.appendText(text)
            realtime.finish()
            withTimeout(RESPONSE_TIMEOUT_MS) { done.await() }
            player.drain()
            val firstDelay = realtime.getFirstAudioDelay().takeIf { it >= 0 } ?: -1
            TtsResult(
                firstAudioDelayMs = firstDelay,
                totalLatencyMs = System.currentTimeMillis() - startedAt,
            )
        } catch (e: TimeoutCancellationException) {
            throw AppError.TtsUpstream(IOException("TTS response timeout"))
        } catch (e: AppError) {
            throw e
        } catch (t: Throwable) {
            throw AppError.TtsUpstream(t)
        } finally {
            runCatching { realtime.close() }
            current = null
            player.release()
            speaking.set(false)
        }
    }

    override fun stop() {
        runCatching { current?.cancelResponse() }
        runCatching { current?.close() }
        current = null
    }

    private class ObservedQwenTtsRealtime(
        private val onFailure: (Throwable) -> Unit,
        param: QwenTtsRealtimeParam,
        callback: QwenTtsRealtimeCallback,
    ) : QwenTtsRealtime(param, callback) {
        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onFailure(t)
            super.onFailure(webSocket, t, response)
        }
    }

    private fun mapRealtimeFailure(error: Throwable): AppError {
        val message = error.message.orEmpty().lowercase()
        return when {
            "401" in message || "403" in message || "unauthor" in message -> AppError.TtsAuthFailed(error)
            else -> AppError.TtsUpstream(error, serverErrorMessage = error.message)
        }
    }

    companion object {
        const val MODEL = "qwen3-tts-flash-realtime"
        const val VOICE = "Kiki"
        const val REALTIME_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime"
        private const val NORMAL_CLOSE = 1000
        private const val RESPONSE_TIMEOUT_MS = 30_000L
    }
}
