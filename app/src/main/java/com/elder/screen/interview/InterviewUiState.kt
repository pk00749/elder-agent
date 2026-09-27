package com.elder.android.screen.interview

import com.elder.android.agent.InterviewAgent
import com.elder.android.data.InterviewSession

enum class InterviewStage {
    PREPARING,
    OPENING,    // v0.9.0 新增：ChatAgent 正在生成主动问候 + TTS 播报中
    READY,
    RECORDING,
    THINKING,
    SPEAKING,
    REVIEW,
    SAVED,
}

data class InterviewUiState(
    val stage: InterviewStage = InterviewStage.PREPARING,
    val session: InterviewSession? = null,
    val elapsedMs: Long = 0,
    val transcript: String? = null,
    val assistantText: String? = null,           // v0.9.1 起：单段回复 ≤25 字（A2），不再分 ack/probe
    val draftText: String? = null,
    val draftSummary: String? = null,
    val ttsFailed: Boolean = false,
    val pendingSaved: Boolean = false,
    val needsConfig: Boolean = false,
    val topError: String? = null,
) {
    val turnNo: Int get() = (session?.turns?.size ?: 0) + 1
    val maxTurns: Int get() = InterviewAgent.MAX_TURNS
    val canRevise: Boolean get() = (session?.turns?.size ?: 0) < maxTurns
}
