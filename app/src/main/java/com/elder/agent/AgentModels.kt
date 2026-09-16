package com.elder.android.agent

import com.elder.android.data.InterviewSession

data class AgentReply(
    val session: InterviewSession,
    val assistantText: String,
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
