package com.elder.android.data.llm

import com.elder.android.error.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * MiniMax M3 OpenAI 兼容客户端。
 *
 * 只依赖标准 `/v1/chat/completions`、SSE 和 tools 协议，不引入 MiniMax Java SDK，
 * 便于 Android 打包和 MockWebServer 测试。
 */
class MiniMaxClient(
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: String = DEFAULT_ENDPOINT,
    private val model: String = DEFAULT_MODEL,
) : LlmClient {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun complete(
        apiKey: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        onDelta: suspend (String) -> Unit,
    ): LlmResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AppError.LlmAuthFailed()
        val requestBody = buildRequest(messages, tools).toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw AppError.LlmUpstream(e, serverErrorMessage = e.message)
        }

        response.use {
            if (!it.isSuccessful) {
                throw mapHttpFailure(it.code, it.body?.string().orEmpty())
            }
            val source = it.body?.source()
                ?: throw AppError.LlmUpstream(IOException("MiniMax returned empty body"))
            parseStream(source, onDelta)
        }
    }

    private fun buildRequest(messages: List<LlmMessage>, tools: List<LlmTool>): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("stream", JsonPrimitive(true))
            put("thinking", buildJsonObject { put("type", JsonPrimitive("disabled")) })
            put("reasoning_split", JsonPrimitive(true))
            put("max_completion_tokens", JsonPrimitive(256))
            put("messages", JsonArray(messages.map(::messageJson)))
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(toolJson(it)) } })
            }
        }

    private fun messageJson(message: LlmMessage): JsonObject =
        buildJsonObject {
            put("role", JsonPrimitive(message.role))
            val content = when {
                message.content != null -> JsonPrimitive(message.content)
                message.toolCalls.isNotEmpty() -> JsonPrimitive("")
                else -> JsonNull
            }
            put("content", content)
            if (message.toolCalls.isNotEmpty()) {
                put(
                    "tool_calls",
                    buildJsonArray {
                        message.toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(call.id))
                                    put("type", JsonPrimitive("function"))
                                    put(
                                        "function",
                                        buildJsonObject {
                                            put("name", JsonPrimitive(call.name))
                                            put("arguments", JsonPrimitive(call.arguments))
                                        },
                                    )
                                }
                            )
                        }
                    },
                )
            }
            message.toolCallId?.let { put("tool_call_id", JsonPrimitive(it)) }
        }

    private fun toolJson(tool: LlmTool): JsonObject =
        buildJsonObject {
            put("type", JsonPrimitive("function"))
            put(
                "function",
                buildJsonObject {
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", tool.parameters)
                },
            )
        }

    private suspend fun parseStream(
        source: okio.BufferedSource,
        onDelta: suspend (String) -> Unit,
    ): LlmResult {
        val content = StringBuilder()
        val calls = linkedMapOf<Int, PartialToolCall>()

        while (!source.exhausted()) {
            val line = source.readUtf8Line()?.trim().orEmpty()
            if (!line.startsWith(DATA_PREFIX)) continue
            val payload = line.removePrefix(DATA_PREFIX).trim()
            if (payload == DONE_PAYLOAD) break
            if (payload.isEmpty()) continue

            val event = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
            event["error"]?.let { throw mapServerError(it) }
            val choice = event["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: continue
            val delta = choice["delta"]?.jsonObject ?: choice["message"]?.jsonObject ?: continue
            delta["content"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let {
                content.append(it)
                onDelta(it)
            }
            delta["tool_calls"]?.jsonArray?.forEach { element ->
                val item = element.jsonObject
                val index = item["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: calls.size
                val partial = calls.getOrPut(index) { PartialToolCall() }
                item["id"]?.jsonPrimitive?.contentOrNull?.let { partial.id = it }
                item["function"]?.jsonObject?.let { function ->
                    function["name"]?.jsonPrimitive?.contentOrNull?.let { partial.name = it }
                    function["arguments"]?.jsonPrimitive?.contentOrNull?.let { partial.arguments.append(it) }
                }
            }
        }

        return LlmResult(
            content = content.toString().trim(),
            toolCalls = calls.values.mapNotNull { it.toCall() },
        )
    }

    private fun mapHttpFailure(status: Int, body: String): AppError = when (status) {
        401, 403 -> AppError.LlmAuthFailed()
        429 -> AppError.LlmRateLimited()
        else -> AppError.LlmUpstream(
            serverErrorCode = status.toString(),
            serverErrorMessage = body.take(300),
        )
    }

    private fun mapServerError(element: JsonElement): AppError {
        val obj = element as? JsonObject
        val code = obj?.get("code")?.jsonPrimitive?.contentOrNull
        val message = obj?.get("message")?.jsonPrimitive?.contentOrNull
        val normalized = "$code $message".lowercase()
        return when {
            "auth" in normalized || "api key" in normalized -> AppError.LlmAuthFailed()
            "rate" in normalized || "limit" in normalized -> AppError.LlmRateLimited()
            else -> AppError.LlmUpstream(serverErrorCode = code, serverErrorMessage = message)
        }
    }

    private data class PartialToolCall(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    ) {
        fun toCall(): LlmToolCall? =
            if (id.isBlank() || name.isBlank()) null
            else LlmToolCall(id = id, name = name, arguments = arguments.toString().ifBlank { "{}" })
    }

    companion object {
        const val DEFAULT_MODEL = "MiniMax-M3"
        const val DEFAULT_ENDPOINT = "https://api.minimax.cn/v1/chat/completions"

        private const val DATA_PREFIX = "data:"
        private const val DONE_PAYLOAD = "[DONE]"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
