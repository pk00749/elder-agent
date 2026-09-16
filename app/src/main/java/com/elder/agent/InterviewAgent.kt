package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.InterviewTurn
import com.elder.android.data.llm.LlmClient
import com.elder.android.data.llm.LlmMessage
import com.elder.android.data.llm.LlmTool
import com.elder.android.data.llm.LlmToolCall
import com.elder.android.error.AppError
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

class InterviewAgent(
    private val llm: LlmClient,
    private val prompts: PromptProvider,
    private val maxTurns: Int = MAX_TURNS,
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun respond(
        apiKey: String,
        session: InterviewSession,
        elderText: String,
        onDelta: suspend (String) -> Unit = {},
    ): AgentTurnResult {
        val normalized = elderText.trim()
        val priorTexts = session.turns.map(InterviewTurn::elderText)

        if (AgentSafety.isEmergency(normalized)) {
            return finalize(apiKey, session, normalized)
        }
        if (AgentSafety.isMoneyOrMedical(normalized)) {
            return completeReply(
                session = session,
                elderText = normalized,
                assistantText = MONEY_MEDICAL_REPLY,
                shouldFinalize = false,
            )
        }
        if (session.turns.size >= maxTurns) {
            return finalize(apiKey, session, "", fallbackAssistant = FALLBACK_REPLY)
        }

        var messages = buildMessages(session, normalized)
        var lastResult = completeWithRetry(apiKey, messages, TOOLS, onDelta)
        var toolRound = 0
        while (lastResult.toolCalls.isNotEmpty() && toolRound < MAX_TOOL_ROUNDS) {
            val call = lastResult.toolCalls.first()
            validateCall(call)
            if (call.name == TOOL_SAVE_DIARY) {
                val args = parseSaveArguments(call)
                return completeDraft(session, normalized, args.text, args.summary, lastResult.content)
            }

            messages = messages + llmMessage(lastResult) + LlmMessage(
                role = "tool",
                content = CLARIFY_RESULT,
                toolCallId = call.id,
            )
            lastResult = completeWithRetry(apiKey, messages, TOOLS, onDelta)
            toolRound += 1
        }

        val assistantText = lastResult.content.trim().take(MAX_REPLY_CHARS).ifBlank { FALLBACK_REPLY }
        val shouldFinalize = AgentSafety.isExplicitClose(normalized) ||
            AgentSafety.dimensionCount(priorTexts + normalized) >= 2 ||
            session.turns.size + 1 >= maxTurns
        if (!shouldFinalize) {
            return completeReply(session, normalized, assistantText, false)
        }
        return finalize(apiKey, session, normalized, fallbackAssistant = assistantText)
    }

    suspend fun summarize(apiKey: String, transcript: String): Pair<String, String> {
        val result = completeWithRetry(
            apiKey = apiKey,
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

    private suspend fun finalize(
        apiKey: String,
        session: InterviewSession,
        elderText: String,
        fallbackAssistant: String = EMERGENCY_REPLY,
    ): AgentTurnResult.Finalize {
        val transcript = (session.turns.map(InterviewTurn::elderText) + elderText)
            .filter(String::isNotBlank)
            .joinToString("\n") { "老人：$it" }
        val result = completeWithRetry(
            apiKey = apiKey,
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
                session = session.copy(
                    status = InterviewStatus.REVIEWING,
                    turns = if (elderText.isBlank()) {
                        session.turns
                    } else {
                        session.turns + turn(session, elderText, fallbackAssistant)
                    },
                    draftText = text,
                    draftSummary = summary,
                    updatedAt = System.currentTimeMillis(),
                ),
                text = text,
                summary = summary,
            )
        )
    }

    private fun completeDraft(
        session: InterviewSession,
        elderText: String,
        text: String,
        summary: String,
        assistantText: String,
    ): AgentTurnResult.Finalize {
        val safeText = text.trim().take(MAX_TEXT_CHARS).ifBlank { elderText.take(MAX_TEXT_CHARS) }
        val safeSummary = summary.trim().take(MAX_SUMMARY_CHARS).ifBlank { safeText.take(MAX_SUMMARY_CHARS) }
        return AgentTurnResult.Finalize(
            AgentFinalDraft(
                session = session.copy(
                    status = InterviewStatus.REVIEWING,
                    turns = session.turns + turn(session, elderText, assistantText.take(MAX_REPLY_CHARS)),
                    draftText = safeText,
                    draftSummary = safeSummary,
                    updatedAt = System.currentTimeMillis(),
                ),
                text = safeText,
                summary = safeSummary,
            )
        )
    }

    private fun completeReply(
        session: InterviewSession,
        elderText: String,
        assistantText: String,
        shouldFinalize: Boolean,
    ): AgentTurnResult.Reply =
        AgentTurnResult.Reply(
            AgentReply(
                session = session.copy(
                    turns = session.turns + turn(session, elderText, assistantText),
                    updatedAt = System.currentTimeMillis(),
                ),
                assistantText = assistantText,
                shouldFinalize = shouldFinalize,
            )
        )

    private fun turn(session: InterviewSession, elderText: String, assistantText: String): InterviewTurn =
        InterviewTurn(
            turnNo = session.turns.size + 1,
            elderText = elderText.take(MAX_ELDER_TEXT_CHARS),
            assistantText = assistantText.take(MAX_REPLY_CHARS),
            createdAt = System.currentTimeMillis(),
        )

    private fun buildMessages(session: InterviewSession, elderText: String): List<LlmMessage> =
        buildList {
            add(LlmMessage(role = "system", content = prompts.system()))
            session.turns.forEach { turn ->
                add(LlmMessage(role = "user", content = turn.elderText))
                add(LlmMessage(role = "assistant", content = turn.assistantText))
            }
            add(LlmMessage(role = "user", content = elderText))
        }

    private fun llmMessage(result: com.elder.android.data.llm.LlmResult): LlmMessage =
        LlmMessage(
            role = "assistant",
            content = result.content.ifBlank { null },
            toolCalls = result.toolCalls,
        )

    private suspend fun completeWithRetry(
        apiKey: String,
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        onDelta: suspend (String) -> Unit = {},
    ): com.elder.android.data.llm.LlmResult {
        var lastError: Throwable? = null
        repeat(retryCount) { attempt ->
            try {
                return llm.complete(apiKey, messages, tools, onDelta)
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

    private fun parseSaveArguments(call: LlmToolCall): SaveArguments {
        val obj = runCatching { json.parseToJsonElement(call.arguments).let { it as JsonObject } }.getOrNull()
            ?: throw AppError.LlmUpstream(serverErrorMessage = "invalid save_diary arguments")
        return SaveArguments(
            text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            summary = obj["summary"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        )
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

    private data class SaveArguments(val text: String, val summary: String)

    companion object {
        const val MAX_TURNS = 8
        const val MAX_REPLY_CHARS = 25
        const val MAX_TEXT_CHARS = 100
        const val MAX_SUMMARY_CHARS = 60
        const val MAX_ELDER_TEXT_CHARS = 500
        const val MAX_TRANSCRIPT_CHARS = 4_000
        private const val MAX_TOOL_ROUNDS = 2
        private const val MAX_RETRIES = 3
        private val RETRY_DELAYS_MS = longArrayOf(500L, 1_000L)
        private const val MONEY_MEDICAL_REPLY = "嗯，咱们聊点别的吧。"
        private const val EMERGENCY_REPLY = "好的，咱们先把今天说的记下来。"
        private const val FALLBACK_REPLY = "嗯，然后呢？"
        private const val CLARIFY_RESULT = "已换到轻松日常话题，请继续问一个短问题。"
        const val TOOL_ASK_CLARIFY = "ask_clarify"
        const val TOOL_SAVE_DIARY = "save_diary"
        private val TOOL_NAMES = setOf(TOOL_ASK_CLARIFY, TOOL_SAVE_DIARY)
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
                put("required", kotlinx.serialization.json.JsonArray(listOf()))
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
                put("required", kotlinx.serialization.json.JsonArray(listOf(
                    kotlinx.serialization.json.JsonPrimitive("text"),
                    kotlinx.serialization.json.JsonPrimitive("summary"),
                )))
            },
        )
        private val TOOLS = listOf(ASK_CLARIFY, SAVE_DIARY)
    }
}
