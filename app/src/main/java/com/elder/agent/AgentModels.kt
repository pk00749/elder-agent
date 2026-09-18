package com.elder.android.agent

import com.elder.android.data.InterviewSession

/**
 * 单轮 Agent 回复；v0.6.0 加 ack（≤10 字共情前置）+ probe（≤25 字追问）拆分。
 * `assistantText` = ack + probe 拼接（≤35 字），用于 TTS 播报；UI 渲染按 ackText / probeText 分别着色。
 */
data class AgentReply(
    val session: InterviewSession,
    val assistantText: String,   // ack + probe 拼接
    val ackText: String,         // ≤10 字共情前置；空串表示无 ack
    val probeText: String,       // ≤25 字追问；空串表示仅 ack
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
