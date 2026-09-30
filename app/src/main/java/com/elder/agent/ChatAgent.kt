// 对应 prd.md §3.1.4 v0.9.0 + §A.16；ChatAgent 主循环（v0.9.0 拆分）
// v0.6.0/0.7.0/0.8.x InterviewAgent → v0.9.0 ChatAgent；工具集 6 → 3
// v0.9.0 新增 open() 主动开问入口；安全 / 记忆 / save 委托 SafetyAgent / MemoryAgent / SaveAgent
package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.db.ElderFactPersistResult
import com.elder.android.data.InterviewTurn
import com.elder.android.data.db.ElderFactRepository
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

/** 时段判定（v0.9.0 ChatAgent.open() 用） */
enum class TimeOfDay { MORNING, NOON, EVENING }

/**
 * v0.9.0 ChatAgent — 主循环（老人每轮回应后调）+ open() 主动开问入口。
 *
 * 工具集 6 → 3：仅 ask_clarify / mark_dimension_covered / MOVE_ON。
 * save_diary 由 ViewModel 显式调 SaveAgent.saveDiary（不在工具集中）。
 * remember_fact / search_memory 由 MemoryAgent 后台独立运行（不在工具集中）。
 */
class ChatAgent(
    private val llmFactory: LlmClientFactory,
    private val prompts: ChatPrompts,
    private val safety: SafetyAgent = SafetyAgent,
    private val memory: MemoryAgent? = null,
    private val save: SaveAgent? = null,
    // v0.10.0 §5: 删除 maxTurns 硬上限;收尾改由 EXPLICIT_CLOSE / mark_dimension_covered ≥2 含 feeling 触发
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** C1 维度跟踪（v0.6.0）：LLM 显式 mark_dimension_covered 累加；session 级状态。 */
    private val coveredDimensions = mutableSetOf<Dimension>()

    // ===== 主循环 respond() =====

    suspend fun respond(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
        onDelta: suspend (String) -> Unit = {},
    ): AgentTurnResult {
        val normalized = elderText.trim()

        // 安全判定（v0.9.0：委托 SafetyAgent，本地判定不上 LLM）
        when (safety.check(normalized)) {
            SafetyAgent.Verdict.EMERGENCY -> return finalizeViaSafety(credentials, session, normalized)
            SafetyAgent.Verdict.MONEY, SafetyAgent.Verdict.MEDICAL -> return completeReply(
                session = session,
                elderText = normalized,
                assistantText = SafetyAgent.SHORT_REPLY,
                shouldFinalize = false,
            )
            SafetyAgent.Verdict.EXPLICIT_CLOSE -> return finalizeViaSafety(credentials, session, normalized)
            // v0.11.0 §3.4：老人主动结束（粤语关键词）→ 礼貌落幕 TTS + 落库
            SafetyAgent.Verdict.ELDER_EXPLICIT_END -> return finalizeViaExplicitEnd(credentials, session, normalized)
            SafetyAgent.Verdict.SAFE -> { /* 走主 LLM 循环 */ }
        }

        var messages = buildMessages(session, normalized, recentSummaries, elderFacts)
        var lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
        var toolRound = 0
        while (lastResult.toolCalls.isNotEmpty() && toolRound < MAX_TOOL_ROUNDS) {
            val call = lastResult.toolCalls.first()
            validateCall(call)
            when (call.name) {
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
                // ===== v0.9.0 兼容分支（chat_v1.txt 不声明；保留给 v0.8.x 测试） =====
                TOOL_SAVE_DIARY -> {
                    val args = parseSaveArguments(call)
                    return completeDraft(session, normalized, args.text, args.summary, lastResult.content)
                }
                TOOL_REMEMBER_FACT -> {
                    val args = parseRememberArgsCompat(call)
                    val persistResult = handleRememberFactCompat(session.id, args)
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool",
                        content = if (persistResult.isNew) "记住了（新增）" else "记住了（已合并到已有事实）",
                        toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                TOOL_SEARCH_MEMORY -> {
                    val args = parseSearchArgsCompat(call)
                    val hits = if (memory != null) memory.searchFacts(args.query, args.typeFilter, args.limit) else emptyList()
                    val hitText = if (hits.isEmpty()) "（未找到相关记忆）" else hits.joinToString("\n") { "[${it.type}, ${it.confidence}] ${it.content}" }
                    messages = messages + llmMessage(lastResult) + LlmMessage(
                        role = "tool", content = hitText, toolCallId = call.id,
                    )
                    lastResult = completeWithRetry(credentials, messages, TOOLS, onDelta)
                }
                else -> throw AppError.LlmUpstream(serverErrorMessage = "unsupported tool ${call.name}")
            }
            toolRound += 1
        }

        // v0.9.1：单段回复 ≤25 字（A2）；不再 splitAckProbe，LLM 输出原样截断。
        val assistantText = lastResult.content.take(MAX_REPLY_CHARS).ifBlank { FALLBACK_REPLY }
        val dimensionReady = coveredDimensions.size >= 2 && Dimension.FEELING in coveredDimensions
        val shouldFinalize = safety.check(normalized) == SafetyAgent.Verdict.EXPLICIT_CLOSE ||
            dimensionReady
        // v0.10.0 §5: 删除 session.turns.size + 1 >= maxTurns 硬截断;软上限 20 轮由 chat_v2.txt §C 软指令引导
        if (!shouldFinalize) {
            return completeReply(session, normalized, assistantText, shouldFinalize = false)
        }
        return finalizeViaSafety(credentials, session, normalized, fallbackAssistant = assistantText)
    }

    // ===== 主动开问 open() =====

    /**
     * v0.9.0 新增：进入访谈屏时调一次，产 ≤25 字问候（不分 ack/probe）。
     * 注入 recentSummaries + elderFacts 做 E6 跨会话回扣。
     * 走 chat_v1.txt §OPEN 段 + 全量 chat_v1（含 §RESPOND）。
     * LLM 失败兜底：SafetyAgent.greetingFallback(timeOfDay) 静态中文，不调 TTS。
     *
     * 第一句**不计 turn 计数**；返回纯字符串，由 ViewModel 调 TTS 播报 + 展示。
     */
    suspend fun open(
        credentials: LlmCredentials,
        timeOfDay: TimeOfDay,
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
        onDelta: suspend (String) -> Unit = {},
    ): String {
        // 兜底链：先决定 fallback，再尝试 LLM
        val fallback = SafetyAgent.greetingFallback(timeOfDay)
        if (recentSummaries.isEmpty() && elderFacts.isEmpty()) {
            // 无注入时直接返回基础问候，不浪费一次 LLM 调用
            return fallback
        }
        return try {
            val messages = listOf(
                LlmMessage(role = "system", content = prompts.openWithContext(recentSummaries, elderFacts)),
                LlmMessage(role = "user", content = openUserPrompt(timeOfDay)),
            )
            val result = completeWithRetry(credentials, messages, tools = emptyList(), onDelta = onDelta)
            val raw = result.content.trim()
            // 截断 ≤25 字
            raw.take(MAX_OPEN_CHARS).ifBlank { fallback }
        } catch (e: Throwable) {
            fallback
        }
    }

    // ===== 兼容老 InterviewAgent 入口 =====

    /** v0.5.0 兼容接口：单次录音 → summarize 为 text + summary（v0.9.0 由 SaveAgent.summarize() 接管）。 */
    suspend fun summarize(credentials: LlmCredentials, transcript: String): Pair<String, String> {
        if (save == null) error("SaveAgent not injected")
        return save.summarize(credentials, transcript)
    }

    /** F5 Background Learning：save_diary 落库后追加调用一次。v0.9.0 由 MemoryAgent.backgroundLearning() 接管。 */
    suspend fun backgroundLearning(credentials: LlmCredentials, transcript: String): Int {
        if (memory == null) return 0
        return memory.backgroundLearning(credentials, transcript)
    }

    // ===== 内部 =====

    private fun openUserPrompt(timeOfDay: TimeOfDay): String = when (timeOfDay) {
        TimeOfDay.MORNING -> "请按早上时段产出开场问候。"
        TimeOfDay.NOON -> "请按中午时段产出开场问候。"
        TimeOfDay.EVENING -> "请按晚上时段产出开场问候。"
    }

    private fun buildMessages(
        session: InterviewSession,
        elderText: String,
        recentSummaries: List<DiarySummary>,
        elderFacts: List<ElderFact>,
    ): List<LlmMessage> = buildList {
        add(LlmMessage(role = "system", content = prompts.respondWithContext(recentSummaries, elderFacts)))
        session.turns.forEach { turn ->
            add(LlmMessage(role = "user", content = turn.elderText))
            add(LlmMessage(role = "assistant", content = turn.assistantText))
        }
        add(LlmMessage(role = "user", content = elderText))
    }

    /**
     * v0.9.1 简化：单参数 assistantText；不再做 ack/probe 拆段。
     * 截断到 MAX_REPLY_CHARS（25）由调用方保证（见 respond 主循环 + safety 短路）。
     */
    private fun completeReply(
        session: InterviewSession,
        elderText: String,
        assistantText: String,
        shouldFinalize: Boolean,
    ): AgentTurnResult.Reply {
        val capped = assistantText.take(MAX_REPLY_CHARS)
        val updatedSession = session.copy(
            turns = session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = capped,
                createdAt = System.currentTimeMillis(),
            ),
        )
        return AgentTurnResult.Reply(
            AgentReply(
                session = updatedSession,
                assistantText = capped,
                shouldFinalize = shouldFinalize,
            ),
        )
    }

    /**
     * v0.9.0 收尾:v0.10.0 §5 删除 maxTurns 分支;触发条件 = EMERGENCY / EXPLICIT_CLOSE / dimensionReady(≥2 含 feeling)。
     * 委托 SaveAgent.saveDiary() 走 save_v3.txt；SaveAgent 未注入则走本地解析（保持兼容）。
     */
    private suspend fun finalizeViaSafety(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
        fallbackAssistant: String = SafetyAgent.EMERGENCY_NOTE,
    ): AgentTurnResult.Finalize {
        val updatedSession = session.copy(
            turns = if (elderText.isBlank()) session.turns else session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = fallbackAssistant,
                createdAt = System.currentTimeMillis(),
            ),
            status = InterviewStatus.REVIEWING,
        )

        val (text, summary) = if (save != null) {
            val s = save.saveDiary(credentials, updatedSession)
            s.text to s.summary
        } else {
            "" to ""  // SaveAgent 未注入时返回空；ViewModel 应始终注入
        }

        return AgentTurnResult.Finalize(
            AgentFinalDraft(
                session = updatedSession,
                text = text,
                summary = summary,
            ),
        )
    }

    /**
     * v0.11.0 §3.4：老人主动结束对话（粤语关键词命中）专用 finalize 路径。
     *
     * 与 finalizeViaSafety 的差异:
     * - 同:走 SaveAgent.saveDiary 出 text / summary 草稿 → Review 阶段让老人保存
     * - 异:设置 farewellText = "好的，今天先聊到这。",ViewModel 用作 TTS 落幕语
     *
     * THINKING/SPEAKING 阶段调用此方法:ViewModel 不打断当前 LLM/TTS 流,
     * 等到主循环自然完成后再 speakOrShow(ELDER_END_GOODBYE) → 触发落幕 TTS。
     */
    private suspend fun finalizeViaExplicitEnd(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
    ): AgentTurnResult.Finalize {
        val updatedSession = session.copy(
            turns = if (elderText.isBlank()) session.turns else session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = SafetyAgent.ELDER_END_GOODBYE,
                createdAt = System.currentTimeMillis(),
            ),
            status = InterviewStatus.REVIEWING,
        )

        val (text, summary) = if (save != null) {
            val s = save.saveDiary(credentials, updatedSession)
            s.text to s.summary
        } else {
            "" to ""
        }

        return AgentTurnResult.Finalize(
            AgentFinalDraft(
                session = updatedSession,
                text = text,
                summary = summary,
                farewellText = SafetyAgent.ELDER_END_GOODBYE,
            ),
        )
    }

    // ===== JSON 解析 =====

    // ===== v0.9.0 兼容方法（chat_v1.txt 不走；保留给 v0.8.x save_diary 工具测试） =====

    private fun completeDraft(
        session: InterviewSession,
        elderText: String,
        text: String,
        summary: String,
        replyFromTool: String,
    ): AgentTurnResult.Finalize {
        // v0.9.1：single-segment reply；不再 splitAckProbe
        val assistantText = (if (replyFromTool.isNotBlank()) replyFromTool else FALLBACK_REPLY)
            .take(MAX_REPLY_CHARS)
        val updatedSession = session.copy(
            turns = session.turns + InterviewTurn(
                turnNo = session.turns.size + 1,
                elderText = elderText,
                assistantText = assistantText,
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

    private suspend fun handleRememberFactCompat(sessionId: String?, args: CompatRememberArgs): com.elder.android.data.db.ElderFactPersistResult {
        // v0.9.0：MemoryAgent 持有 elderFactRepo；直接调它的内部方法会破坏封装，
        // 所以这里通过 MemoryAgent 的 remember_fact 入口委托（v0.9.0 加一个公开方法）。
        // 简化路径：若 MemoryAgent 未注入，返回 noop。
        val mem = memory ?: return com.elder.android.data.db.ElderFactPersistResult(id = "noop", isNew = false)
        return try {
            mem.rememberFactCompat(sessionId, args.type, args.content, args.confidence)
        } catch (e: Throwable) {
            com.elder.android.data.db.ElderFactPersistResult(id = "error", isNew = false)
        }
    }

    private fun parseSaveArguments(call: LlmToolCall): CompatSaveArgs {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid save_diary arguments")
        return CompatSaveArgs(
            text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseRememberArgsCompat(call: LlmToolCall): CompatRememberArgs {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid remember_fact arguments")
        return CompatRememberArgs(
            type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            content = obj["content"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            confidence = obj["confidence"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
    }

    private fun parseSearchArgsCompat(call: LlmToolCall): CompatSearchArgs {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid search_memory arguments")
        return CompatSearchArgs(
            query = obj["query"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            typeFilter = obj["type"]?.jsonPrimitive?.contentOrNull,
            limit = obj["limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 5,
        )
    }

    private data class CompatSaveArgs(val text: String, val summary: String)
    private data class CompatRememberArgs(val type: String, val content: String, val confidence: String)
    private data class CompatSearchArgs(val query: String, val typeFilter: String?, val limit: Int)

    // ===== 主 JSON 解析 =====

    private fun parseDimensionArg(call: LlmToolCall): Dimension {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
        val raw = obj?.get("dim")?.jsonPrimitive?.contentOrNull.orEmpty()
        return Dimension.fromRaw(raw) ?: Dimension.TIME
    }

    private fun parseMoveOnArg(call: LlmToolCall): String {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
        return obj?.get("stage")?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "next_dimension" }
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

    companion object {
        // v0.10.0 §5: 删除 MAX_TURNS = 8 硬上限;轮数软上限 20 由 chat_v2.txt §C 软指令引导
        const val MAX_OPEN_CHARS = 25  // v0.9.0 新增：open() 第一句 ≤25 字
        const val MAX_TEXT_CHARS = 100  // D1（兼容路径使用）
        const val MAX_SUMMARY_CHARS = 60  // D2（兼容路径使用）
        // v0.9.1：A2 单段回复 ≤25 字；取消 ack/probe 双段拆段与总长 ≤35 约束
        const val MAX_REPLY_CHARS = 25  // A2 单段回复

        // v0.9.0 工具集：仅 ChatAgent 暴露给 LLM 的 3 个工具
        const val TOOL_ASK_CLARIFY = "ask_clarify"
        const val TOOL_MARK_DIMENSION = "mark_dimension_covered"
        const val TOOL_MOVE_ON = "MOVE_ON"
        // v0.9.0 兼容（chat_v1.txt 不声明；保留给 v0.8.x 测试和过渡路径）
        const val TOOL_SAVE_DIARY = "save_diary"
        const val TOOL_REMEMBER_FACT = "remember_fact"
        const val TOOL_SEARCH_MEMORY = "search_memory"

        private val TOOL_NAMES = setOf(TOOL_ASK_CLARIFY, TOOL_MARK_DIMENSION, TOOL_MOVE_ON, TOOL_SAVE_DIARY, TOOL_REMEMBER_FACT, TOOL_SEARCH_MEMORY)

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
            description = "节奏控制（E4）：连续两轮追问同一维度时切换。",
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

        // ===== v0.9.0 兼容工具（chat_v1.txt 不声明；保留给 v0.8.x 测试和过渡） =====

        private val SAVE_DIARY = LlmTool(
            name = TOOL_SAVE_DIARY,
            description = "v0.9.0 兼容：访谈信息足够时通过此工具保存最终日记。",
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
                put("required", JsonArray(listOf(JsonPrimitive("text"), JsonPrimitive("summary"))))
            },
        )

        private val REMEMBER_FACT = LlmTool(
            name = TOOL_REMEMBER_FACT,
            description = "v0.9.0 兼容：保存一条长期事实。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("type") {
                        put("type", "string")
                        put("enum", JsonArray(Dimension.entries.map { JsonPrimitive(it.raw) } + JsonPrimitive("health") + JsonPrimitive("preference")))
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
            description = "v0.9.0 兼容：查询已记忆的事实。",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", "string")
                        put("description", "搜索关键词")
                    }
                    putJsonObject("type") {
                        put("type", "string")
                        put("description", "可选；限定 type")
                    }
                    putJsonObject("limit") {
                        put("type", "integer")
                        put("description", "返回上限，默认 5")
                    }
                }
                put("required", JsonArray(listOf(JsonPrimitive("query"))))
            },
        )

        // v0.9.0：暴露给 LLM 的 3 个主推工具（chat_v1.txt 声明）
        private val TOOLS_RECOMMENDED = listOf(ASK_CLARIFY, MARK_DIMENSION, MOVE_ON)
        // v0.9.0：保留 3 个兼容工具（save_diary / remember_fact / search_memory）供 v0.8.x 测试和过渡路径
        private val TOOLS_LEGACY = listOf(ASK_CLARIFY, MARK_DIMENSION, MOVE_ON, SAVE_DIARY, REMEMBER_FACT, SEARCH_MEMORY)
        // v0.9.0 公开 TOOLS = 兼容全集；prompt 推荐只声明前 3 个
        private val TOOLS = TOOLS_LEGACY

        internal const val MAX_TOOL_ROUNDS = 3  // v0.6.0 工具多，容许 3 轮
        internal const val MAX_RETRIES = 3
        internal val RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
        private const val FALLBACK_REPLY = "嗯，然后呢？"
        private const val CLARIFY_RESULT = "已换到轻松日常话题，请继续问一个短问题。"
    }
}
