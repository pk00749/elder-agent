package com.elder.android.agent

import com.elder.android.data.InterviewSession

/**
 * 单轮 Agent 回复；v0.9.1 起统一单段（取消 v0.6.0 ack + probe 双段拆段）。
 * `assistantText` ≤25 字（A2），用于 TTS 播报 + UI 单段渲染（28sp 次级）。
 *
 * v0.6.0 / v0.7.0 / v0.8.x / v0.9.0 拆分字段（ackText / probeText）已在 v0.9.1 删除；
 * 旧 call site（InterviewAgentTest_v2.kt 的 3 个 ack/probe 拆段测试）改为「单段 ≤25 字」断言。
 */
data class AgentReply(
    val session: InterviewSession,
    val assistantText: String,   // ≤25 字单段回复（A2）
    val shouldFinalize: Boolean,
)

data class AgentFinalDraft(
    val session: InterviewSession,
    val text: String,
    val summary: String,
)

sealed interface AgentTurnResult {
    data class Reply(
        val value: AgentReply,
    ) : AgentTurnResult

    data class Finalize(
        val value: AgentFinalDraft,
    ) : AgentTurnResult
}

/**
 * v0.6.0 C1 修订：维度跟踪。LLM 显式调 `mark_dimension_covered(dim)` 累加，
 * 累计 ≥2 项（含 feeling）且 E2 走到 how-felt 阶段才允许 finalize。
 */
enum class Dimension(val raw: String) {
    TIME("time"),
    PLACE("place"),
    PERSON("person"),
    EVENT("event"),
    FEELING("feeling");

    companion object {
        fun fromRaw(raw: String): Dimension? = entries.firstOrNull { it.raw == raw }
    }
}
