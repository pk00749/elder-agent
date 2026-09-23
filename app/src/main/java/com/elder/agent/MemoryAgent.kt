// 对应 prd.md §3.1.4 F3 / F5 + §A.16；v0.9.0 MemoryAgent 拆分
// v0.9.0 独立 LLM 调用 + Room 持久化；不走 ChatAgent 对话上下文（不污染主循环）
// 加载 chat_v1.txt 之外：运行模式为 memory_v1.txt background_learning
package com.elder.android.agent

import com.elder.android.data.db.ElderFactPersistResult
import com.elder.android.data.db.ElderFactRepository
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * v0.9.0 MemoryAgent — save_diary 落库后追加一次 LLM 抽 facts_to_remember[]。
 * 解析后每条走 remember_fact 工具同一路径（ElderFactRepository 校验 + 去重 + 写入 Room）。
 * 失败静默 catch，不阻塞 diary 保存路径。
 */
class MemoryAgent(
    private val llmFactory: LlmClientFactory,
    private val prompts: MemoryPrompts,
    private val elderFactRepo: ElderFactRepository? = null,
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * F5 Background Learning：save_diary 落库后追加调用一次。
     * @return 抽取并写入的事实条数；失败返回 0。
     */
    suspend fun backgroundLearning(
        credentials: LlmCredentials,
        transcript: String,
    ): Int {
        if (elderFactRepo == null) return 0
        return try {
            val userContent = buildString {
                append("请以 background_learning 模式整理以上访谈，并返回 facts_to_remember。\n\n")
                append(transcript.take(MAX_TRANSCRIPT_CHARS))
            }
            val result = completeWithRetry(
                credentials = credentials,
                messages = listOf(
                    LlmMessage(role = "system", content = prompts.memory()),
                    LlmMessage(role = "user", content = userContent),
                ),
                tools = emptyList(),
            )
            val facts = parseBackgroundLearningJson(result.content)
            facts.take(MAX_BACKGROUND_FACTS).forEach { args ->
                handleRememberFact(args)
            }
            facts.size
        } catch (e: Throwable) {
            0
        }
    }

    /**
     * 主动查询事实（E6 跨会话回扣用；v0.9.0 仍走 Room SQL LIKE 匹配）。
     * ChatAgent 主循环不直接调用本方法（工具集 6 → 3 已移除 search_memory），
     * 仍保留接口供将来 search UI（如"找过去提过的事"）使用。
     */
    suspend fun searchFacts(
        query: String,
        typeFilter: String? = null,
        limit: Int = 5,
    ): List<ElderFact> {
        if (elderFactRepo == null) return emptyList()
        return elderFactRepo.searchMemory(query, typeFilter, limit)
    }

    /**
     * v0.9.0 兼容入口：ChatAgent 主循环调用 remember_fact 工具时委托本方法。
     * 与 backgroundLearning 内部 handleRememberFact 行为一致；type / confidence / content 非法时返回
     * [ElderFactPersistResult] id="invalid"，不抛异常（ChatAgent 已 catch）。
     */
    suspend fun rememberFactCompat(
        sessionId: String?,
        type: String,
        content: String,
        confidence: String,
    ): ElderFactPersistResult {
        if (elderFactRepo == null) return ElderFactPersistResult(id = "noop", isNew = false)
        return try {
            elderFactRepo.rememberFact(
                type = type,
                content = content,
                confidence = confidence,
                sourceSessionId = sessionId,
            )
        } catch (e: IllegalArgumentException) {
            ElderFactPersistResult(id = "invalid", isNew = false)
        } catch (e: Throwable) {
            ElderFactPersistResult(id = "error", isNew = false)
        }
    }

    // ===== 内部 =====

    private suspend fun handleRememberFact(args: FactArgs): ElderFactPersistResult {
        if (elderFactRepo == null) return ElderFactPersistResult(id = "noop", isNew = false)
        return try {
            elderFactRepo.rememberFact(
                type = args.type,
                content = args.content,
                confidence = args.confidence,
                sourceSessionId = null,  // v0.9.0 MemoryAgent 不绑定 sessionId（后置 background learning）
            )
        } catch (e: IllegalArgumentException) {
            ElderFactPersistResult(id = "invalid", isNew = false)
        } catch (e: Throwable) {
            ElderFactPersistResult(id = "error", isNew = false)
        }
    }

    private fun parseBackgroundLearningJson(raw: String): List<FactArgs> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val obj = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject
        }.getOrNull() ?: return emptyList()
        val arr = obj["facts_to_remember"] as? JsonArray ?: return emptyList()
        return arr.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            FactArgs(
                type = item["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                confidence = item["confidence"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ).takeIf { it.type.isNotBlank() && it.content.isNotBlank() }
        }
    }

    private suspend fun completeWithRetry(
        credentials: LlmCredentials,
        messages: List<LlmMessage>,
        tools: List<com.elder.android.data.llm.LlmTool>,
    ): com.elder.android.data.llm.LlmResult {
        val apiKey = credentials.apiKeyForCurrentProvider()
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                return llmFactory.current(credentials).complete(apiKey, messages, tools)
            } catch (e: com.elder.android.error.AppError.LlmAuthFailed) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < retryCount - 1) kotlinx.coroutines.delay(RETRY_DELAYS_MS[attempt])
            }
        }
        throw lastError ?: com.elder.android.error.AppError.LlmUpstream()
    }

    private data class FactArgs(val type: String, val content: String, val confidence: String)

    companion object {
        const val MAX_TRANSCRIPT_CHARS = 4_000
        const val MAX_BACKGROUND_FACTS = 5  // F5 单次抽取 ≤5
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
    }
}
