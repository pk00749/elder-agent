// 对应 PRD §3.1.9 + §10.4 阿里云百炼 ASR 客户端（v3.0 MVP 唯一上游）
//
// 协议：DashScope WebSocket duplex（Qwen-Audio-3.0-ASR-Flash-Streaming）
//   - 端点：wss://dashscope.aliyuncs.com/api-ws/v1/inference
//   - Auth：Authorization: Bearer <API_KEY>
//   - 流：run-task → continue-task × N → finish-task → result-generated × N → task-finished
//   - 音频：base64 m4a（run-task parameters.format = "m4a"）
//
// WorkspaceId 与 model 硬编码（PRD §A.1.b）；仅 API Key 由用户在设置页输入。
// 失败映射 AppError（PRD §6.4）：401/403 → AsrAuthFailed；429 → AsrRateLimited；4xx → AsrBadRequest；5xx → AsrUpstream；空文 → AsrEmptyTranscript
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
import java.io.IOException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit

class AsrApiClient(
    private val client: OkHttpClient = defaultClient(),
    private val wsUrl: String = WS_URL,
) {
    suspend fun transcribe(
        apiKey: String,
        audioFile: File,
    ): AsrResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.AsrAuthFailed()
        val audioB64 = try {
            Base64.getEncoder().encodeToString(audioFile.readBytes())
        } catch (e: IOException) {
            throw AppError.AsrUpstream(e)
        }
        runBailianWs(apiKey, audioB64)
    }

    private suspend fun runBailianWs(apiKey: String, audioB64: String): AsrResult {
        val taskId = UUID.randomUUID().toString()
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        val deferred = CompletableDeferred<AsrResult>()
        var lastSentence: AsrResult? = null

        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = JSONObject(text)
                    val action = msg.getJSONObject("header").optString("action")
                    when (action) {
                        "result-generated" -> {
                            val out = msg.optJSONObject("payload")?.optJSONObject("output") ?: return
                            val trans = out.optJSONObject("transcription") ?: return
                            val sentence = trans.optString("text", "")
                            val isEnd = trans.optBoolean("sentence_end", false)
                            if (sentence.isNotBlank() && isEnd) {
                                lastSentence = AsrResult(
                                    text = sentence,
                                    confidence = trans.optDouble("confidence", -1.0).toFloat()
                                        .takeIf { it >= 0f },
                                    httpStatus = 200,
                                )
                            }
                        }
                        "task-finished" -> {
                            if (!deferred.isCompleted) {
                                deferred.complete(lastSentence ?: AsrResult("", null, 200))
                            }
                            webSocket.close(WS_CLOSE_NORMAL, "done")
                        }
                        "task-failed" -> {
                            val statusCode = msg.getJSONObject("header").optInt("status_code", 500)
                            if (!deferred.isCompleted) {
                                deferred.completeExceptionally(mapStatusToError(statusCode))
                            }
                            webSocket.close(WS_CLOSE_NORMAL, "failed")
                        }
                    }
                } catch (_: Throwable) {
                    // 忽略单条消息解析错误；最终结果由 task-finished/task-failed 收口
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!deferred.isCompleted) {
                    deferred.completeExceptionally(AppError.AsrUpstream(t))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }
        })

        return try {
            ws.send(buildRunTaskMessage(taskId))
            ws.send(buildContinueTaskMessage(taskId, audioB64))
            ws.send(buildFinishTaskMessage(taskId))

            val result = withTimeoutOrNull(ASR_TIMEOUT_MS) {
                deferred.await()
            } ?: throw AppError.AsrUpstream(IOException("Bailian ASR timeout"))
            if (result.text.isBlank()) throw AppError.AsrEmptyTranscript()
            result
        } catch (e: TimeoutCancellationException) {
            throw AppError.AsrUpstream(IOException("Bailian ASR timeout"))
        } finally {
            ws.close(WS_CLOSE_NORMAL, "done")
        }
    }

    private fun buildRunTaskMessage(taskId: String): String =
        JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "run-task")
                put("task_id", taskId)
                put("streaming", "duplex")
            })
            put("payload", JSONObject().apply {
                put("model", BAILIAN_MODEL)
                put("parameters", JSONObject().apply {
                    put("sample_rate", SAMPLE_RATE)
                    put("format", AUDIO_FORMAT)
                    put("language_hints", JSONArray().apply {
                        put("zh")
                        put("yue")
                    })
                })
                put("input", JSONObject())
            })
        }.toString()

    private fun buildContinueTaskMessage(taskId: String, audioB64: String): String =
        JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "continue-task")
                put("task_id", taskId)
            })
            put("payload", JSONObject().apply {
                put("input", JSONObject().apply {
                    put("audio", audioB64)
                })
            })
        }.toString()

    private fun buildFinishTaskMessage(taskId: String): String =
        JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "finish-task")
                put("task_id", taskId)
            })
            put("payload", JSONObject().apply {
                put("input", JSONObject())
            })
        }.toString()

    private fun mapStatusToError(statusCode: Int): AppError = when {
        statusCode == 401 || statusCode == 403 -> AppError.AsrAuthFailed()
        statusCode == 429 -> AppError.AsrRateLimited()
        statusCode in 400..499 -> AppError.AsrBadRequest()
        else -> AppError.AsrUpstream()
    }

    companion object {
        // PRD §A.1.b：阿里云百炼 ASR 配置（硬编码；API Key 仍由用户输入）
        const val BAILIAN_WORKSPACE_ID = "llm-svrk4hi977f8t2fe"
        const val BAILIAN_PROVIDER = "bailian"
        const val BAILIAN_MODEL = "Qwen-Audio-3.0-ASR-Flash-Streaming"
        const val WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"
        const val AUDIO_FORMAT = "m4a"
        const val SAMPLE_RATE = 16000

        private const val ASR_TIMEOUT_MS = 30_000L
        private const val WS_CLOSE_NORMAL = 1000

        fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")  // §7 sanitize 不打明文 API Key
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
