// 对应 PRD §3.1.9 / §A.12 MiniMax ASR 客户端（v0.7.0 默认 Provider）
//
// 协议（对齐参考 Python 例子）：
//   - 端点：https://api.minimax.cn/v1/speech_to_text   （HTTP REST）
//   - Auth：Authorization: Bearer <API_KEY>           （与 MiniMax LLM 共用 Key，
//                                                     §5.11 minimax_api_key_enc）
//   - 请求：multipart/form-data，字段：
//       file = <wav 文件>
//       model = "asr-1.0"
//       stream = "true"                                （服务端 SSE 增量返回 delta）
//   - 响应：text/event-stream，逐行 data: {...}
//       data: {"delta":"今天"}        ← 增量文本
//       data: {"finish":true}         ← 收口
//
// 历史 WebSocket 实现（占位字段 session.start / audio.chunk / transcript.partial
// / transcript.final / session.finish）已废弃；现走标准 HTTPS + multipart + SSE。
package com.elder.android.data.asr

import com.elder.android.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MiniMax 语音识别客户端（§A.12）。
 * Provider 标识固定 `minimax_realtime`（§5.11 asr_config.asr_provider / diary_entry.asr_provider 落库值）。
 * 由 ServiceLocator.asrClient() 在 Provider = minimax_realtime 时返回本实例。
 */
class MiniMaxAsrClient(
    private val client: OkHttpClient = defaultClient(),
    private val restUrl: String = REST_URL,
) : AsrClient {

    override val providerRaw: String = MINIMAX_ASR_PROVIDER
    override val model: String = MINIMAX_ASR_MODEL

    override suspend fun openSession(
        apiKey: String,
        onPartial: (String) -> Unit,
    ): RealtimeAsrSession = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.AsrAuthFailed()
        StreamingMiniMaxSession(
            apiKey = apiKey,
            client = client,
            restUrl = restUrl,
            onPartial = onPartial,
        )
    }

    override suspend fun transcribe(apiKey: String, audioFile: File): AsrResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) throw AppError.AsrAuthFailed()
            if (!audioFile.exists() || audioFile.length() == 0L) {
                throw AppError.AsrBadRequest(IOException("Audio file is missing or empty"))
            }
            uploadAndReadSse(client, restUrl, audioFile, apiKey, onPartial = {})
        }

    /**
     * 流式录音会话（§A.12）：把 [appendAudio] 累积的 PCM 16kHz/mono/16-bit 包装成
     * `.wav`（在首个 [appendAudio] 调用时写入 RIFF/WAVE 头），[finish] 时一次性 POST。
     * 用临时文件缓冲避免长录音占用堆内存。
     */
    private class StreamingMiniMaxSession(
        private val apiKey: String,
        private val client: OkHttpClient,
        private val restUrl: String,
        private val onPartial: (String) -> Unit,
    ) : RealtimeAsrSession {
        private val tempFile: File = File.createTempFile("minimax_asr_", ".wav")
        private val output = FileOutputStream(tempFile)
        private val lock = Any()
        private var headerWritten = false
        private val finished = AtomicBoolean(false)
        private val closed = AtomicBoolean(false)

        override fun appendAudio(audio: ByteArray) {
            if (audio.isEmpty() || closed.get() || finished.get()) return
            synchronized(lock) {
                if (!headerWritten) {
                    writeWavHeader(output, sampleRate = SAMPLE_RATE, channels = 1, dataSize = 0)
                    headerWritten = true
                }
                output.write(audio)
            }
        }

        override suspend fun finish(): AsrResult = withContext(Dispatchers.IO) {
            if (!finished.compareAndSet(false, true)) {
                throw AppError.AsrUpstream(IOException("MiniMax ASR session already finished"))
            }
            synchronized(lock) {
                if (!headerWritten) {
                    writeWavHeader(output, sampleRate = SAMPLE_RATE, channels = 1, dataSize = 0)
                    headerWritten = true
                }
                runCatching { output.close() }
            }
            try {
                uploadAndReadSse(client, restUrl, tempFile, apiKey, onPartial)
            } finally {
                runCatching { tempFile.delete() }
            }
        }

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                synchronized(lock) {
                    runCatching { output.close() }
                    runCatching { tempFile.delete() }
                }
            }
        }
    }

    companion object {
        const val MINIMAX_ASR_PROVIDER = "minimax_realtime"
        const val MINIMAX_ASR_MODEL = "asr-1.0"
        const val REST_URL = "https://api.minimax.cn/v1/speech_to_text"
        const val STREAM_FLAG = "true"
        const val SAMPLE_RATE = 16_000

        private val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
        private const val MAX_ERROR_BODY_BYTES = 4L * 1024L
        private const val DATA_PREFIX = "data:"

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

        /**
         * 上传 .wav 到 MiniMax REST endpoint，读取 SSE 增量 delta 累积为最终文本。
         * 同时被 [MiniMaxAsrClient.transcribe] 与 [StreamingMiniMaxSession.finish] 调用。
         */
        internal suspend fun uploadAndReadSse(
            client: OkHttpClient,
            restUrl: String,
            audioFile: File,
            apiKey: String,
            onPartial: (String) -> Unit,
        ): AsrResult {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", MINIMAX_ASR_MODEL)
                .addFormDataPart("stream", STREAM_FLAG)
                .addFormDataPart(
                    name = "file",
                    filename = audioFile.name,
                    body = audioFile.asRequestBody(WAV_MEDIA_TYPE),
                )
                .build()

            val request = Request.Builder()
                .url(restUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            val accumulator = StringBuilder()
            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw AppError.AsrUpstream(IOException("MiniMax ASR network failure: ${e.message}", e))
            }
            response.use {
                if (!response.isSuccessful) {
                    throw mapHttpFailure(response)
                }
                val source = response.body?.source()
                    ?: throw AppError.AsrUpstream(IOException("MiniMax ASR response body is null"))
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith(DATA_PREFIX)) continue
                    val payload = trimmed.removePrefix(DATA_PREFIX).trim()
                    if (payload.isEmpty()) continue
                    try {
                        val event = JSONObject(payload)
                        val error = event.optJSONObject("error")
                        if (error != null) throw mapStreamFailure(error)
                        val delta = event.optString("delta")
                        if (delta.isNotEmpty()) {
                            accumulator.append(delta)
                            // §A.12 MiniMax SSE 协议：delta 是增量片段，需在客户端累积后
                            // 再回调 onPartial（与 Bailian WS 的 text+stash 累积语义对齐），
                            // 否则 UI 每次 delta 都覆盖 state.transcript，前段文字丢失。
                            onPartial(accumulator.toString())
                        }
                        if (event.optBoolean("finish", false)) break
                    } catch (e: AppError) {
                        throw e
                    } catch (_: Throwable) {
                        // 忽略单条事件解析错误；最终由 finish / HTTP 状态收口。
                    }
                }
            }
            val text = accumulator.toString().trim()
            if (text.isEmpty()) throw AppError.AsrEmptyTranscript()
            return AsrResult(text = text, confidence = null, httpStatus = 200)
        }

        /**
         * 把上游 4xx/5xx 映射成 AppError，并把 response body 里能解析出的 error code/message
         * 塞进 serverErrorCode / serverErrorMessage，让 UI 不再只看到 "配置有误" 这一句笼统文案。
         * Body 摘要过 §7 sanitize：仅取服务端 error 字段（code / message / type），不传播原始音频。
         */
        private fun mapHttpFailure(response: Response): AppError {
            val code = response.code
            val (errCode, errMessage) = readErrorBody(response)
            return when (code) {
                401, 403 -> AppError.AsrAuthFailed(IOException("MiniMax ASR HTTP $code"))
                429 -> AppError.AsrRateLimited(IOException("MiniMax ASR HTTP 429"))
                in 400..499 -> AppError.AsrBadRequest(
                    cause = IOException("MiniMax ASR HTTP $code"),
                    serverErrorCode = errCode,
                    serverErrorMessage = errMessage,
                )
                else -> AppError.AsrUpstream(
                    cause = IOException("MiniMax ASR HTTP $code"),
                    serverErrorCode = errCode,
                    serverErrorMessage = errMessage,
                )
            }
        }

        /**
         * 读取 response body 摘要（最多 4 KiB），从中解析常见 error JSON 形状。
         * 返回 (errorCode, errorMessage)；任一字段缺失时为 null。
         * 与 §7 sanitize 对齐：只透服务端 error 字段，不含 PII / 音频内容。
         */
        private fun readErrorBody(response: Response): Pair<String?, String?> {
            return try {
                val raw = response.peekBody(MAX_ERROR_BODY_BYTES).string().trim()
                if (raw.isBlank()) return null to null
                val obj = JSONObject(raw)
                val err = obj.optJSONObject("error") ?: obj
                val code = err.optString("code").takeIf { it.isNotBlank() }
                    ?: err.optString("type").takeIf { it.isNotBlank() }
                val message = err.optString("message").takeIf { it.isNotBlank() }
                code to message
            } catch (_: Throwable) {
                null to null
            }
        }

        private fun mapStreamFailure(error: JSONObject): AppError {
            val type = error.optString("type")
            val code = error.optString("code")
            val message = error.optString("message")
            val normalized = "$type $code $message".lowercase()
            return when {
                normalized.contains("auth") ||
                    normalized.contains("api key") ||
                    normalized.contains("apikey") -> AppError.AsrAuthFailed()
                normalized.contains("throttl") || normalized.contains("rate limit") ->
                    AppError.AsrRateLimited()
                normalized.contains("invalid") || normalized.contains("bad request") ->
                    AppError.AsrBadRequest(
                        serverErrorCode = code.ifBlank { null },
                        serverErrorMessage = message.ifBlank { null },
                    )
                else -> AppError.AsrUpstream(
                    serverErrorCode = code.ifBlank { null },
                    serverErrorMessage = message.ifBlank { null },
                )
            }
        }

        /**
         * 写入 16-bit PCM RIFF/WAVE 头；RIFF / data chunk size 都用真实 [dataSize] 算对，
         * 不留占位 0（之前留 0 占位假设服务端按文件大小解析，严格 wav parser 会拒）。
         */
        private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, dataSize: Int) {
            val byteRate = sampleRate * channels * 2
            val totalRiffSize = 36 + dataSize
            out.write("RIFF".toByteArray())
            out.write(intToLe(totalRiffSize))
            out.write("WAVE".toByteArray())
            out.write("fmt ".toByteArray())
            out.write(intToLe(16))
            out.write(shortToLe(1))
            out.write(shortToLe(channels.toShort()))
            out.write(intToLe(sampleRate))
            out.write(intToLe(byteRate))
            out.write(shortToLe((channels * 2).toShort()))
            out.write(shortToLe(16))
            out.write("data".toByteArray())
            out.write(intToLe(dataSize))
        }

        private fun intToLe(v: Int): ByteArray = byteArrayOf(
            (v and 0xff).toByte(),
            ((v shr 8) and 0xff).toByte(),
            ((v shr 16) and 0xff).toByte(),
            ((v shr 24) and 0xff).toByte(),
        )

        private fun shortToLe(v: Short): ByteArray = byteArrayOf(
            (v.toInt() and 0xff).toByte(),
            ((v.toInt() shr 8) and 0xff).toByte(),
        )
    }
}
