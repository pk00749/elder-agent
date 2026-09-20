// 对应 prd.md §3.1.4 v0.6.0；Agent 行为约束 A6/E/F 节落地。
// v0.5.0 → v0.6.0 主要变化：
//   1. respond() 加 recentSummaries + elderFacts 参数；buildMessages 调 systemWithContext
//   2. 单轮回复拆 ack（≤10 字共情前置）+ probe（≤25 字追问），总长 ≤35
//   3. C1 维度判定：LLM 显式调 mark_dimension_covered 替代 AgentSafety 字典计数
//   4. 新工具 mark_dimension_covered / MOVE_ON / remember_fact / search_memory
//   5. F5 background learning：save_diary 后追加一次 LLM 抽 facts
package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.InterviewTurn
import com.elder.android.data.db.ElderFactPersistResult
import com.elder.android.data.db.ElderFactRepository
import com.elder.android.data.llm.LlmClient
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.llm.LlmMessage
import com.elder.android.data.llm.LlmResult
import com.elder.android.data.llm.LlmTool
import com.elder.android.data.llm.LlmToolCall
import com.elder.android.error.AppError
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

class InterviewAgent(
    private val llmFactory: LlmClientFactory,
    private val prompts: PromptProvider,
    /** v0.6.0 F3：remember_fact / search_memory 落地；null 时工具调用退化为 no-op（测试场景） */
    private val elderFactRepo: ElderFactRepository? = null,
    private val maxTurns: Int = MAX_TURNS,
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** C1 维度跟踪（v0.6.0）：LLM 显式 mark_dimension_covered 累加；session 级状态。 */
    private val coveredDimensions = mutableSetOf<Dimension>()

    suspend fun respond(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
        onDelta: suspend (String) -> Unit = {},
    ): AgentTurnResult {
        val normalized = elderText.trim()
        if (AgentSafety.isEmergency(normalized)) {
            return finalize(credentials, session, normalized)
        }
        if (AgentSafety.isMoneyOrMedical(normalized)) {
            return completeReply(session, normalized, ack = "", probe = MONEY_MEDICAL_REPLY, shouldFinalize = false)
        }
        if (session.turns.size >= maxTurns) {
            return finalize(credentials, session, "", fallbackAssistant = FALLBACK_REPLY)
        }

        var messages = buildMessages(session, normalized, recentSummaries, elderFacts)
        var lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
        var toolRound = 0
        while (lastResult.toolCalls.isNotEmpty() && toolRound < MAX_TOOL_ROUNDS) {
            val call = lastResult.toolCalls.first()
            validateCall(call)
            when (call.name) {
                TOOL_SAVE_DIARY -> {
                    val args = parseSaveArguments(call)
                    return completeDraft(session, normalized, args.text, args.summary, lastResult.content)
                }
                TOOL_ASK_CLARIFY -> {
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool", content = CLARIFY_RESULT, toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                TOOL_MARK_DIMENSION -> {
                    val dim = parseDimensionArg(call)
                    coveredDimensions.add(dim)
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool", content = "已记录：${dim.raw} 维度已覆盖（累计 ${coveredDimensions.size}/2）", toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                TOOL_MOVE_ON -> {
                    val stage = parseMoveOnArg(call)
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool", content = "已切换节奏：$stage", toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                TOOL_REMEMBER_FACT -> {
                    val args = parseRememberArgs(call)
                    val persistResult = handleRememberFact(session.id, args)
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool",
                        content = if (persistResult.isNew) "记住了（新增）" else "记住了（已合并到已有事实）",
                        toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                TOOL_SEARCH_MEMORY -> {
                    val args = parseSearchArgs(call)
                    val hits = handleSearchMemory(args)
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool", content = renderSearchHits(hits), toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
            }
            toolRound += 1
        }

        val (ack, probe) = splitAckProbe(lastResult.content)
        val dimensionReady = coveredDimensions.size >= 2 && Dimension.FEELING in coveredDimensions
        val shouldFinalize = AgentSafety.isExplicitClose(normalized) ||
            dimensionReady ||
            session.turns.size + 1 >= maxTurns
        if (!shouldFinalize) {
            return completeReply(session, normalized, ack, probe, shouldFinalize = false)
        }
        return finalize(credentials, session, normalized, fallbackAssistant = ack + probe)
    }

    /**
     * F5 Background Learning：save_diary 落库后追加调用一次，让 LLM 抽 facts_to_remember[]。
     * 解析后每条走 remember_fact 工具同一路径。失败静默 catch，不阻塞 diary 保存路径。
     */
    suspend fun backgroundLearning(credentials: LlmCredentials, transcript: String): Int {
        if (elderFactRepo == null) return 0
        return try {
            val result = completeWithRetry(
                credentials = credentials,
                messages = listOf(
                    LlmMessage(role = "system", content = prompts.save()),
                    LlmMessage(role = "user", content = "请以 background_learning 模式整理以上访谈，并返回 facts_to_remember。\n\n$transcript".take(MAX_TRANSCRIPT_CHARS)),
                ),
                tools = emptyList(),
            )
            val facts = parseBackgroundLearningJson(result.content)
            facts.take(MAX_BACKGROUND_FACTS).forEach { args ->
                handleRememberFact(sessionId = null, args = args)
            }
            facts.size
        } catch (e: Throwable) {
            0
        }
    }

    /**
     * v0.5.0 兼容接口：单次录音 → summarize 为 text + summary（v0.6.0 仍保留给 PendingDiaryBackfill 用）。
     * 走 save_v2.txt prompt（已合并 background_learning 模式，但普通调用仍按 text/summary 解析）。
     */
    suspend fun summarize(credentials: LlmCredentials, transcript: String): Pair<String, String> {
        val result = completeWithRetry(
            credentials = credentials,
            messages = listOf(
                LlmMessage(role = "system", content = prompts.save()),
                LlmMessage(role = "user", content = transcript.take(MAX_TRANSCRIPT_CHARS)),
            ),
            tools = emptyList(),
        )
        val parsed = parseFinalJson(result.content)
        val text = parsed?.first?.take(MAX_TEXT_CHARS).orEmpty()
            .ifBlank { transcript.take(MAX_TEXT_CHARS) }
        val summary = parsed?.second?.take(MAX_SUMMARY_CHARS).orEmpty()
            .ifBlank { text.take(MAX_SUMMARY_CHARS) }
        return text to summary
    }

    // ===== 内部：消息构建 =====

    private fun buildMessages(
        session: InterviewSession,
        elderText: String,
        recentSummaries: List<DiarySummary>,
        elderFacts: List<ElderFact>,
    ): List<LlmMessage> = buildList {
        // v0.6.0 systemWithContext 三块注入（§F4）；fallback 到旧 system() 兼容旧 PromptProvider
        val systemContent = try {
            prompts.systemWithContext(recentSummaries, elderFacts)
        } catch (e: NoSuchMethodError) {
            @Suppress("DEPRECATION")
            prompts.system()
        }
        add(LlmMessage(role = "system", content = systemContent))
        session.turns.forEach { turn ->
            add(LlmMessage(role = "user", content = turn.elderText))
            add(LlmMessage(role = "assistant", content = turn.assistantText))
        }
        add(LlmMessage(role = "user", content = elderText))
    }

    /**
     * A6 ack/probe 拆分：找第一个中文标点（，。！？…），之前 ≤10 字作为 ack；
     * 之后 ≤25 字作为 probe；总长 ≤35。
     */
    private fun splitAckProbe(raw: String): Pair<String, String> {
        val text = raw.trim()
        if (text.isEmpty()) return "" to FALLBACK_REPLY
        val boundary = text.indexOfFirst { it in "，。！？…" }
        return if (boundary in 1..MAX_ACK_CHARS) {
            val ack = text.substring(0, boundary + 1).trim().take(MAX_ACK_CHARS)
            val probe = text.substring(boundary + 1).trim().take(MAX_PROBE_CHARS)
                .ifBlank { FALLBACK_REPLY }
            ack to probe
        } else {
            val ack = text.take(MAX_ACK_CHARS)
            val probe = text.drop(MAX_ACK_CHARS).trim().take(MAX_PROBE_CHARS)
                .ifBlank { FALLBACK_REPLY }
            ack to probe
        }
    }

    // ===== 内部：完成态 =====

    private suspend fun finalize(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
        fallbackAssistant: String = EMERGENCY_REPLY,
    ): AgentTurnResult.Finalize {
        val transcript = (session.turns.map(InterviewTurn::elderText) + elderText)
            .filter(String::isNotBlank)
            .joinToString("\n") { "老人：$it" }
        val result = completeWithRetry(
            credentials = credentials,
            messages = listOf(
                LlmMessage(role = "system", content = prompts.save()),
                LlmMessage(role = "user", content = transcript),
            ),
            tools = emptyList(),
        )
        val parsed = parseFinalJson(result.content)
        val text = parsed?.first?.take(MAX_TEXT_CHARS).orEmpty()
            .ifBlank { elderText.take(MAX_TEXT_CHARS) }
        val summary = parsed?.second?.take(MAX_SUMMARY_CHARS).orEmpty()
            .ifBlank { text.take(MAX_SUMMARY_CHARS) }
        return AgentTurnResult.Finalize(
            AgentFinalDraft(
                session = session.copy(status = InterviewStatus.REVIEWING),
                text = text,
                summary = summary,
            ),
        )
    }

    private fun completeReply(
        session: InterviewSession,
        elderText: String,
        ack: String,
        probe: String,
        shouldFinalize: Boolean,
    ): AgentTurnResult.Reply {
        val assistantText = (ack + probe).take(MAX_REPLY_TOTAL_CHARS)
        val updatedSession = session.copy(
            turns = session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = assistantText,
                ack = ack.ifBlank { null },
                probe = probe.ifBlank { null },
                createdAt = System.currentTimeMillis(),
            ),
        )
        return AgentTurnResult.Reply(
            AgentReply(
                session = updatedSession,
                assistantText = assistantText,
                ackText = ack,
                probeText = probe,
                shouldFinalize = shouldFinalize,
            ),
        )
    }

    private fun completeDraft(
        session: InterviewSession,
        elderText: String,
        text: String,
        summary: String,
        ackProbeFromTool: String,
    ): AgentTurnResult.Finalize {
        val (ack, probe) = if (ackProbeFromTool.isNotBlank()) splitAckProbe(ackProbeFromTool) else "" to FALLBACK_REPLY
        val assistantText = (ack + probe).take(MAX_REPLY_TOTAL_CHARS)
        val updatedSession = session.copy(
            turns = session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = assistantText,
                ack = ack.ifBlank { null },
                probe = probe.ifBlank { null },
                createdAt = System.currentTimeMillis(),
            ),
            status = InterviewStatus.REVIEWING,
        )
        val finalText = text.take(MAX_TEXT_CHARS)
        val finalSummary = summary.take(MAX_SUMMARY_CHARS)
            .ifBlank { finalText.take(MAX_SUMMARY_CHARS) }
        return AgentTurnResult.Finalize(
            AgentFinalDraft(session = updatedSession, text = finalText, summary = finalSummary),
        )
    }

    // ===== 工具处理 =====

    private suspend fun handleRememberFact(sessionId: String?, args: RememberFactArgs): ElderFactPersistResult {
        if (elderFactRepo == null) return ElderFactPersistResult(id = "noop", isNew = false)
        return try {
            elderFactRepo.rememberFact(
                type = args.type,
                content = args.content,
                confidence = args.confidence,
                sourceSessionId = sessionId,
            )
        } catch (e: IllegalArgumentException) {
            // LLM 传了非法 type/confidence/content 长度 — 静默忽略，不阻塞访谈
            ElderFactPersistResult(id = "invalid", isNew = false)
        } catch (e: Throwable) {
            ElderFactPersistResult(id = "error", isNew = false)
        }
    }

    private suspend fun handleSearchMemory(args: SearchMemoryArgs): List<ElderFact> {
        if (elderFactRepo == null) return emptyList()
        return elderFactRepo.searchMemory(args.query, args.typeFilter, args.limit)
    }

    private fun renderSearchHits(hits: List<ElderFact>): String {
        if (hits.isEmpty()) return "（未找到相关记忆）"
        return hits.joinToString("\n") { "[${it.type}, ${it.confidence}] ${it.content}" }
    }

    // ===== JSON 解析 =====

    private fun parseSaveArguments(call: LlmToolCall): SaveArguments {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid save_diary arguments")
        return SaveArguments(
            text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseDimensionArg(call: LlmToolCall): Dimension {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
        val raw = obj?.get("dim")?.jsonPrimitive?.contentOrNull.orEmpty()
        return Dimension.fromRaw(raw) ?: Dimension.TIME
    }

    private fun parseMoveOnArg(call: LlmToolCall): String {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
        return obj?.get("stage")?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "next_dimension" }
    }

    private fun parseRememberArgs(call: LlmToolCall): RememberFactArgs {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid remember_fact arguments")
        return RememberFactArgs(
            type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            confidence = obj["confidence"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseSearchArgs(call: LlmToolCall): SearchMemoryArgs {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid search_memory arguments")
        return SearchMemoryArgs(
            query = obj["query"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            typeFilter = obj["type"]?.jsonPrimitive?.contentOrNull,
            limit = obj["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5,
        )
    }

    private fun parseBackgroundLearningJson(raw: String): List<RememberFactArgs> {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return emptyList()
        val obj = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject
        }.getOrNull() ?: return emptyList()
        val arr = obj["facts_to_remember"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            RememberFactArgs(
                type = item["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                content = item["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                confidence = item["confidence"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            ).takeIf { it.type.isNotBlank() && it.content.isNotBlank() }
        }
    }

    private fun parseFinalJson(raw: String): Pair<String, String>? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)) as JsonObject
        }.getOrNull() ?: return null
        val text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty()
        return text to summary
    }

    private fun llmMessage(result: LlmResult): LlmMessage = LlmMessage(
        role = "assistant",
        content = result.content.ifBlank { null },
        toolCalls = result.toolCalls,
    )

    private suspend fun completeWithRetry(
        credentials: LlmCredentials,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        onDelta: suspend (String) -> Unit = {},
    ): LlmResult {
        val apiKey = credentials.apiKeyForCurrentProvider()
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                return llmFactory.current(credentials).complete(apiKey, messages, tools, onDelta)
            } catch (e: AppError.LlmAuthFailed) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                if (attempt < retryCount - 1) delay(RETRY_DELAYS_MS[attempt])
            }
        }
        throw lastError ?: AppError.LlmUpstream()
    }

    private fun validateCall(call: LlmToolCall) {
        if (call.name !in TOOL_NAMES) {
            throw AppError.LlmUpstream(serverErrorMessage = "unsupported tool ${call.name}")
        }
    }

    private data class SaveArguments(val text: String, val summary: String)
    private data class RememberFactArgs(val type: String, val content: String, val confidence: String)
    private data class SearchMemoryArgs(val query: String, val typeFilter: String?, val limit: Int)

    companion object {
        const val MAX_TURNS = 8
        const val MAX_REPLY_CHARS = 25              // A2 单条追问
        const val MAX_ACK_CHARS = 10                 // A6 共情前置
        const val MAX_PROBE_CHARS = 25              // A2 追问
        const val MAX_REPLY_TOTAL_CHARS = 35         // A6 单轮总长
        const val MAX_TEXT_CHARS = 100              // D1
        const val MAX_SUMMARY_CHARS = 60            // D2
        const val MAX_ELDER_TEXT_CHARS = 500
        const val MAX_TRANSCRIPT_CHARS = 4_000
        const val MAX_BACKGROUND_FACTS = 5          // F5 单次抽取 ≤5
        private const val MAX_TOOL_ROUNDS = 3       // v0.6.0 工具多，容许 3 轮
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
        private const val MONEY_MEDICAL_REPLY = "嗯，咱们聊点别的吧。"
        private const val EMERGENCY_REPLY = "好的，咱们先把今天说的记下来。"
        private const val FALLBACK_REPLY = "嗯，然后呢？"
        private const val CLARIFY_RESULT = "已换到轻松日常话题，请继续问一个短问题。"

        const val TOOL_ASK_CLARIFY = "ask_clarify"
        const val TOOL_SAVE_DIARY = "save_diary"
        const val TOOL_MARK_DIMENSION = "mark_dimension_covered"  // v0.6.0
        const val TOOL_MOVE_ON = "MOVE_ON"                          // v0.6.0
        const val TOOL_REMEMBER_FACT = "remember_fact"             // v0.6.0
        const val TOOL_SEARCH_MEMORY = "search_memory"             // v0.6.0

        private val TOOL_NAMES = setOf(
            TOOL_ASK_CLARIFY,
            TOOL_SAVE_DIARY,
            TOOL_MARK_DIMENSION,
            TOOL_MOVE_ON,
            TOOL_REMEMBER_FACT,
            TOOL_SEARCH_MEMORY,
        )

        private val ASK_CLARIFY = LlmTool(
            name = TOOL_ASK_CLARIFY,
            description = "当老人提到金钱、验证码、链接或医疗诊断时，换到轻松日常话题。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("reason") {
                        put("type", "string")
                        put("description", "需要换话题的原因")
                    }
                }
                put("required", JsonArray(emptyList()))
            },
        )

        private val SAVE_DIARY = LlmTool(
            name = TOOL_SAVE_DIARY,
            description = "访谈信息足够、老人明确结束或达到轮数上限时，保存最终日记。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "不超过100个汉字的日记正文")
                    }
                    putJsonObject("summary") {
                        put("type", "string")
                        put("description", "不超过60个汉字的一句话摘要")
                    }
                }
                put("required", JsonArray(listOf(
                    JsonPrimitive("text"),
                    JsonPrimitive("summary"),
                )))
            },
        )

        private val MARK_DIMENSION = LlmTool(
            name = TOOL_MARK_DIMENSION,
            description = "标记某一维度已被访谈覆盖（C1 维度判定）。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("dim") {
                        put("type", "string")
                        put("enum", JsonArray(Dimension.entries.map { JsonPrimitive(it.raw) }))
                        put("description", "time / place / person / event / feeling")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("dim"))))
            },
        )

        private val MOVE_ON = LlmTool(
            name = TOOL_MOVE_ON,
            description = "节奏控制（E4）：连续两轮同一维度时切换。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("stage") {
                        put("type", "string")
                        put("enum", JsonArray(listOf(JsonPrimitive("next_dimension"), JsonPrimitive("closing"))))
                        put("description", "下一维度或收尾")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("stage"))))
            },
        )

        private val REMEMBER_FACT = LlmTool(
            name = TOOL_REMEMBER_FACT,
            description = "保存一条长期事实（人物/地点/事件/偏好/健康）。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        put("enum", JsonArray(Dimension.entries.map { JsonPrimitive(it.raw) } + JsonPrimitive("health")))
                        put("description", "person / place / event / preference / health")
                    }
                    putJsonObject("content") {
                        put("type", "string")
                        put("description", "事实正文，≤100 字")
                    }
                    putJsonObject("confidence") {
                        put("type", "string")
                        put("enum", JsonArray(listOf(JsonPrimitive("high"), JsonPrimitive("medium"), JsonPrimitive("low"))))
                        put("description", "high = 老人主动说，medium = 推断，low = 不确定")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("type"), JsonPrimitive("content"), JsonPrimitive("confidence"))))
            },
        )

        private val SEARCH_MEMORY = LlmTool(
            name = TOOL_SEARCH_MEMORY,
            description = "查询已记忆的事实（按关键词 LIKE 匹配）。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "搜索关键词（老人刚才说的人物/地点/事件）")
                    }
                    putJsonObject("type") {
                        put("type", "string")
                        put("description", "可选；限定 type（person/place/event/preference/health）")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "返回上限，默认 5")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("query"))))
            },
        )

        private val TOOLS = listOf(
            ASK_CLARIFY,
            SAVE_DIARY,
            MARK_DIMENSION,
            MOVE_ON,
            REMEMBER_FACT,
            SEARCH_MEMORY,
        )
    }
}
