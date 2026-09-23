// 对应 prd.md §3.1.4 B1-B4 + §A.16；v0.9.0 SafetyAgent 拆分
// v0.9.0 把 AgentSafety.isEmergency / isMoneyOrMedical / isExplicitClose 抽到 SafetyAgent
// 纯本地 object，**不上 LLM**；emergency 路径省 1 次 LLM 调用
// 兼容层 AgentSafety.kt 保留供既有测试使用（@Deprecated）
package com.elder.android.agent

/**
 * v0.9.0 SafetyAgent（纯本地判定，不调 LLM）。
 *
 * 命中关键词后由 ChatAgent 委托：
 * - EMERGENCY → 立即调 SaveAgent.saveDiary 收尾，不调 ChatAgent 主循环
 * - MONEY / MEDICAL → 固定 reply（MONEY_MEDICAL_REPLY），绕过 LLM
 * - EXPLICIT_CLOSE → 标记收尾（shouldFinalize = true）
 * - SAFE → 走 ChatAgent 主循环
 */
object SafetyAgent {

    enum class Verdict { EMERGENCY, MONEY, MEDICAL, EXPLICIT_CLOSE, SAFE }

    /** v0.9.0 open() LLM 失败兜底；按时段返回静态中文（不调 LLM / 不调 TTS） */
    fun greetingFallback(timeOfDay: TimeOfDay): String = when (timeOfDay) {
        TimeOfDay.MORNING -> "早上好，今天想聊什么？"
        TimeOfDay.NOON -> "中午好，今天过得怎样？"
        TimeOfDay.EVENING -> "晚上好，今天有什么想说的？"
    }

    /**
     * 文本安全判定。优先级：emergency > medical > money > explicit_close > safe。
     */
    fun check(text: String): Verdict {
        val normalized = text.trim()
        if (normalized.isEmpty()) return Verdict.SAFE
        if (AgentSafety.isEmergency(normalized)) return Verdict.EMERGENCY
        // medical 优先于 money（医疗关键词更具体；金钱关键词范围更广）
        if (AgentSafety.isMedical(normalized)) return Verdict.MEDICAL
        if (AgentSafety.isMoney(normalized)) return Verdict.MONEY
        if (AgentSafety.isExplicitClose(normalized)) return Verdict.EXPLICIT_CLOSE
        return Verdict.SAFE
    }

    /** v0.9.0 公开固定 reply 常量（原 InterviewAgent.MONEY_MEDICAL_REPLY 上移） */
    const val SHORT_REPLY = "嗯，咱们聊点别的吧。"
    const val EMERGENCY_NOTE = "好的，咱们先把今天说的记下来。"
}
