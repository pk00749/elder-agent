// 对应 prd.md §3.1.9 v0.8.0 + AGENTS.md §A.15：OpenAI Chat Completions 兼容客户端基类
// MiniMax / 千问 / DeepSeek 三 Provider 协议同构（SSE 流 + tools / tool_calls）；本类封装通用 HTTP +
// SSE 解析 + 错误映射，子类只需配置 endpoint / model / 可选 extraRequestBody。
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
 * OpenAI Chat Completions 兼容基类。
 *
 * 子类必须 override [endpoint] / [model]；可选 override [extraRequestBody] 注入供应商特有字段
 * （如 MiniMax 的 `thinking` / `reasoning_split`）。SSE 解析、HTTP 状态码映射、SSE 内嵌 error 映射
 * 三 Provider 行为统一，由基类承载。
 */
abstract class OpenAiLlmClient(
    private val client: OkHttpClient = defaultClient(),
) : LlmClient {
    protected abstract val endpoint: String
    protected abstract val model: String

    /** 注入 OpenAI 标准字段之外的供应商特有 body 字段；默认空。 */
    protected open fun extraRequestBody(obj: kotlinx.serialization.json.JsonObjectBuilder) {}

    /** 注入标准 HTTP Header；默认只设 Authorization / Content-Type。 */
    protected open fun extraHeaders(builder: Request.Builder) {}

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
            .also { extraHeaders(it) }
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
                ?: throw AppError.LlmUpstream(IOException("upstream returned empty body"))
            parseStream(source, onDelta)
        }
    }

    private fun buildRequest(messages: List<LlmMessage>, tools: List<LlmTool>): JsonObject =
        buildJsonObject {
            put("model", JsonPrimitive(model))
            put("stream", JsonPrimitive(true))
            put("messages", JsonArray(messages.map(::messageJson)))
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray { tools.forEach { add(toolJson(it)) } })
            }
            extraRequestBody(this)
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
                                },
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

    protected open fun mapHttpFailure(status: Int, body: String): AppError = when (status) {
        401, 403 -> AppError.LlmAuthFailed()
        429 -> AppError.LlmRateLimited()
        else -> AppError.LlmUpstream(
            serverErrorCode = status.toString(),
            serverErrorMessage = body.take(300),
        )
    }

    protected open fun mapServerError(element: JsonElement): AppError {
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
