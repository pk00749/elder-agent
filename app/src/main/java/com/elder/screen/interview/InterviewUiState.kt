package com.elder.android.screen.interview

import com.elder.android.agent.InterviewAgent
import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus  // v0.10.0 §5: canRevise 用

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
    // v0.10.0 §5: maxTurns 改为软上限提示(参考值 SOFT_TURN_HINT = 20),不再参与最终化判定
    val maxTurns: Int get() = InterviewAgent.SOFT_TURN_HINT
    // canRevise 不再受 maxTurns 限制;改由 session.status + 老人主动触发决定
    val canRevise: Boolean get() = session?.status?.let { it == InterviewStatus.SAVED } == true
}
