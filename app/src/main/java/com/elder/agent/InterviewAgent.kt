// 对应 prd.md §3.1.4 v0.6.0 + v0.7.0；v0.9.0 拆分为 ChatAgent + SafetyAgent + MemoryAgent + SaveAgent
// 本文件保留为**兼容层**：v0.9.0 内部构造 ChatAgent 并委托；既有用例（PromptProvider 注入、TOOL_SAVE_DIARY 常量等）保持。
// v0.9.0 后续 PR 可删除本类，所有 call site 切到 ChatAgent。
package com.elder.android.agent

import com.elder.android.data.InterviewSession
import com.elder.android.data.db.ElderFactRepository
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.LlmCredentials
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

/**
 * v0.9.0 InterviewAgent 兼容层：内部委托 ChatAgent / SafetyAgent / MemoryAgent / SaveAgent。
 *
 * 保持 v0.5.0 - v0.8.x 的公开 API：
 * - respond() / summarize() / backgroundLearning()
 * - v0.10.0 §5: 删除 MAX_TURNS 硬上限;轮数软上限 20 由 chat_v2.txt §C 软指令引导
 * - 保留 MAX_REPLY_CHARS / MAX_TEXT_CHARS / MAX_SUMMARY_CHARS（v0.9.1 起取消 ack/probe 双段拆段）
 * - TOOL_ASK_CLARIFY / TOOL_SAVE_DIARY / TOOL_MARK_DIMENSION / TOOL_MOVE_ON / TOOL_REMEMBER_FACT / TOOL_SEARCH_MEMORY
 *
 * v0.9.0 新增（仅供 ChatAgent.open() / 测试使用）：
 * - MAX_OPEN_CHARS / TimeOfDay / open()（v0.9.0 实现在 ChatAgent）
 */
class InterviewAgent(
    private val llmFactory: LlmClientFactory,
    /** v0.6.0 PromptProvider：v0.9.0 内部适配为 ChatPrompts（仅取 system + save + memory 字段）。 */
    private val prompts: PromptProvider,
    private val elderFactRepo: ElderFactRepository? = null,
    // v0.10.0 §5: 删除 maxTurns 硬上限
    private val retryCount: Int = MAX_RETRIES,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** v0.9.0 内部 ChatAgent 实例；公开 API 通过本字段转发。 */
    private val chatAgent: ChatAgent by lazy {
        val chatPrompts = adapterPrompt(prompts)
        // v0.9.0：MemoryAgent / SaveAgent 需要各自的 prompt 类型，从 chatPrompts 包装出对应接口
        val memoryPrompts = object : MemoryPrompts {
            override fun memory(): String = (prompts as? PromptProvider)?.save() ?: ""  // save_v3 兼容 background_learning
        }
        val savePrompts = object : SavePrompts {
            override fun save(): String = (prompts as? PromptProvider)?.save() ?: ""
            override fun saveForBackgroundLearning(): String = (prompts as? PromptProvider)?.save() ?: ""
        }
        val memoryAgent = if (elderFactRepo != null) MemoryAgent(llmFactory, memoryPrompts, elderFactRepo) else null
        val saveAgent = SaveAgent(llmFactory, savePrompts)
        ChatAgent(
            llmFactory = llmFactory,
            prompts = chatPrompts,
            safety = SafetyAgent,
            memory = memoryAgent,
            save = saveAgent,
            retryCount = retryCount,  // v0.10.0 §5: 删除 maxTurns 参数
        )
    }

    /** v0.9.0 主动开问入口（向后兼容，ViewModel 可直接调） */
    suspend fun open(
        credentials: LlmCredentials,
        timeOfDay: TimeOfDay,
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
    ): String = chatAgent.open(credentials, timeOfDay, recentSummaries, elderFacts)

    /** v0.6.0/0.7.0/0.8.x 主循环：内部走 ChatAgent.respond()。 */
    suspend fun respond(
        credentials: LlmCredentials,
        session: InterviewSession,
        elderText: String,
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
        onDelta: suspend (String) -> Unit = {},
    ): AgentTurnResult = chatAgent.respond(credentials, session, elderText, recentSummaries, elderFacts, onDelta)

    /** v0.5.0 兼容接口：单次录音 → summarize 为 text + summary。委托 SaveAgent。 */
    suspend fun summarize(credentials: LlmCredentials, transcript: String): Pair<String, String> =
        chatAgent.summarize(credentials, transcript)

    /** F5 Background Learning：save_diary 落库后追加调用一次。委托 MemoryAgent。 */
    suspend fun backgroundLearning(credentials: LlmCredentials, transcript: String): Int =
        chatAgent.backgroundLearning(credentials, transcript)

    /** v0.9.0 内部：把旧 PromptProvider 适配为 ChatPrompts（系统段复用 system()；save / memory 走 save_v3.txt）。 */
    private fun adapterPrompt(p: PromptProvider): ChatPrompts = object : ChatPrompts {
        override fun openWithContext(
            recentSummaries: List<DiarySummary>,
            elderFacts: List<ElderFact>,
        ): String {
            // v0.9.0：open() 调用直接走 system() 内容 + 三块注入（兼容老 PromptProvider）
            return try {
                p.systemWithContext(recentSummaries, elderFacts)
            } catch (e: Throwable) {
                p.system()
            }
        }

        override fun respondWithContext(
            recentSummaries: List<DiarySummary>,
            elderFacts: List<ElderFact>,
        ): String {
            return try {
                p.systemWithContext(recentSummaries, elderFacts)
            } catch (e: Throwable) {
                p.system()
            }
        }
    }

    companion object {
        // ===== v0.5.0 / v0.6.0 / v0.7.0 / v0.8.x 兼容常量 =====
        // v0.9.1 起：取消 ack/probe 双段拆段常量（MAX_ACK_CHARS / MAX_PROBE_CHARS / MAX_REPLY_TOTAL_CHARS）。
        // 保留字段名兼容老测试 / 老 call site 引用，但 v0.9.1+ 不再使用 —— 见 ChatAgent.MAX_REPLY_CHARS。
        // v0.10.0 §5: 删除 MAX_TURNS = 8 硬上限;轮数软上限 20 由 chat_v2.txt §C 软指令引导
        // 仅供 UI 显示(进度提示)与 revise 时软参考;不参与最终化判定
        const val SOFT_TURN_HINT = 20
        const val MAX_REPLY_CHARS = 25
        @Deprecated("v0.9.1 removed ack/probe split")
        const val MAX_ACK_CHARS = 10
        @Deprecated("v0.9.1 removed ack/probe split")
        const val MAX_PROBE_CHARS = 25
        @Deprecated("v0.9.1 removed ack/probe split")
        const val MAX_REPLY_TOTAL_CHARS = 35
        const val MAX_TEXT_CHARS = 100  // D1
        const val MAX_SUMMARY_CHARS = 60  // D2
        const val MAX_ELDER_TEXT_CHARS = 500
        const val MAX_TRANSCRIPT_CHARS = 4_000
        const val MAX_RETRIES = 3
        const val MAX_BACKGROUND_FACTS = 5  // F5
        const val MAX_OPEN_CHARS = 25  // v0.9.0 新增

        const val TOOL_ASK_CLARIFY = "ask_clarify"
        const val TOOL_SAVE_DIARY = "save_diary"
        const val TOOL_MARK_DIMENSION = "mark_dimension_covered"
        const val TOOL_MOVE_ON = "MOVE_ON"
        const val TOOL_REMEMBER_FACT = "remember_fact"
        const val TOOL_SEARCH_MEMORY = "search_memory"

        @Deprecated("v0.9.0 InterviewAgent 内部不再实现；保留供旧测试 import。")
        val TOOLS_LEGACY: List<LlmTool> = listOf(
            LlmTool(
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
                    put("required", JsonArray(listOf(JsonPrimitive("text"), JsonPrimitive("summary"))))
                },
            ),
            LlmTool(
                name = TOOL_REMEMBER_FACT,
                description = "保存一条长期事实。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("type") {
                            put("type", "string")
                            put("description", "person / place / event / preference / health")
                        }
                        putJsonObject("content") {
                            put("type", "string")
                            put("description", "事实正文，≤100 字")
                        }
                        putJsonObject("confidence") {
                            put("type", "string")
                            put("description", "high / medium / low")
                        }
                    }
                    put("required", JsonArray(listOf(JsonPrimitive("type"), JsonPrimitive("content"), JsonPrimitive("confidence"))))
                },
            ),
            LlmTool(
                name = TOOL_SEARCH_MEMORY,
                description = "查询已记忆的事实。",
                parameters = buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "搜索关键词")
                        }
                        putJsonObject("limit") {
                            put("type", "integer")
                            put("description", "返回上限")
                        }
                    }
                    put("required", JsonArray(listOf(JsonPrimitive("query"))))
                },
            ),
        )
    }
}
