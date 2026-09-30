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
    val assistantText: String? = null,
    val draftText: String? = null,
    val draftSummary: String? = null,
    val ttsFailed: Boolean = false,
    val pendingSaved: Boolean = false,
    val needsConfig: Boolean = false,
    val topError: String? = null,
    // v0.11.0 §3.3: 顶栏 Toast 当前显示的 LLM 回复文字;null = 不显示
    val llmReplyToastText: String? = null,
    // v0.11.0 §3.4: 落幕语文本(走 finalizeViaExplicitEnd 时设置;ViewModel 用来 TTS 播报而非 summary)
    val farewellText: String? = null,
    // v0.11.0 §3.4: 语音退出黄条是否需要展示;默认 true,首次关闭后写入 prefs 不再弹
    val showVoiceEndHint: Boolean = true,
) {
    val turnNo: Int get() = (session?.turns?.size ?: 0) + 1
    val maxTurns: Int get() = InterviewAgent.SOFT_TURN_HINT
    val canRevise: Boolean get() = session?.status?.let { it == InterviewStatus.SAVED } == true
}
