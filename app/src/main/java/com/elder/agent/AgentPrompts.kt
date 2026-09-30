package com.elder.android.agent

import android.content.Context

/**
 * 对应 prd.md §3.1.4 v0.6.0 + v0.7.0 + v0.10.0 修订；v0.9.0 拆为 4 个 prompt 接口。
 * v0.7.0 修订（system_v3 / save_v3）：A3 明确"用粤语回答"，对齐 §A.13 MiniMax Cantonese_KindWoman TTS 音色。
 * v0.9.0 拆分：ChatPrompts / SafetyPrompts / MemoryPrompts / SavePrompts；老 PromptProvider 保留兼容层。
 * v0.10.0 修订（chat_v2 / system_v4）：
 *  - CHAT_PROMPT 升 v1 → v2：OPEN 段首句强化粵語(≥3 词),鼓励引导老人讲今天;RESPOND 段允许铺垫场景(总和 ≤25 字仍受 A2)
 *  - C 收尾删除 8 轮硬上限,改软上限 20 轮软指令
 *  - 静态兜底 SafetyAgent.greetingFallback() 改粵語(由 SafetyAgent.kt 独立 commit 处理)
 */

/** v0.5.0 / v0.6.0 / v0.7.0 / v0.8.x 兼容接口。v0.9.0 由 [ChatPrompts] 替代。 */
@Deprecated(
    message = "v0.9.0 拆为 ChatPrompts / SafetyPrompts / MemoryPrompts / SavePrompts；本接口保留仅供旧测试",
    replaceWith = ReplaceWith("ChatPrompts"),
)
interface PromptProvider {
    fun system(): String
    fun systemWithContext(
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
    ): String = system()
    fun save(): String
}

/** v0.9.0 ChatAgent 专用：返回带三块注入的 chat_v1.txt 完整 prompt。 */
interface ChatPrompts {
    /** 进入访谈屏时的开场 prompt（§OPEN 段）。注入 recentSummaries + elderFacts。 */
    fun openWithContext(
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
    ): String

    /** 老人每轮回应后的主循环 prompt（§RESPOND 段）。注入 recentSummaries + elderFacts。 */
    fun respondWithContext(
        recentSummaries: List<DiarySummary> = emptyList(),
        elderFacts: List<ElderFact> = emptyList(),
    ): String
}

/** v0.9.0 SafetyAgent 专用：返回 safety_v1.txt 模板（实际不被使用，仅占位）。 */
interface SafetyPrompts {
    fun safety(): String
}

/** v0.9.0 MemoryAgent 专用：返回 memory_v1.txt 背景学习 prompt。 */
interface MemoryPrompts {
    fun memory(): String
}

/** v0.9.0 SaveAgent 专用：返回 save_v3.txt（保留双模式：普通 save + background learning）。 */
interface SavePrompts {
    /** 普通 save_diary 模式：返回 {text, summary, audio_segments} */
    fun save(): String
    /** background_learning 模式：返回 {text, summary, facts_to_remember}（v0.7.0 已合并进 save_v3） */
    fun saveForBackgroundLearning(): String = save()
}

class AgentPrompts(private val context: Context) : PromptProvider, ChatPrompts, SafetyPrompts, MemoryPrompts, SavePrompts {

    // ===== ChatPrompts =====

    override fun openWithContext(
        recentSummaries: List<DiarySummary>,
        elderFacts: List<ElderFact>,
    ): String {
        val template = read(CHAT_PROMPT)
        return template
            .replace("{{elder_facts_block}}", renderElderFactsBlock(elderFacts))
            .replace("{{recent_summaries_block}}", renderRecentSummariesBlock(recentSummaries))
    }

    override fun respondWithContext(
        recentSummaries: List<DiarySummary>,
        elderFacts: List<ElderFact>,
    ): String {
        // v0.9.0：§OPEN 段在 chat_v1.txt 头部；§RESPOND 段紧随其后；同一文件同一注入。
        // 若未来需严格分段，可用 buildMessages 切分；当前实现直接返回完整 prompt。
        return openWithContext(recentSummaries, elderFacts)
    }

    // ===== SafetyPrompts =====

    override fun safety(): String = read(SAFETY_PROMPT)

    // ===== MemoryPrompts =====

    override fun memory(): String = read(MEMORY_PROMPT)

    // ===== SavePrompts =====

    override fun save(): String = read(SAVE_PROMPT)  // 实现 SavePrompts.save()

    override fun saveForBackgroundLearning(): String = read(SAVE_PROMPT)

    // ===== 兼容老 PromptProvider（v0.6.0 测试用） =====

    @Deprecated(message = "v0.9.0 由 respondWithContext 替代")
    override fun system(): String = read(SYSTEM_PROMPT_V1)

    @Deprecated(message = "v0.9.0 由 respondWithContext 替代")
    override fun systemWithContext(
        recentSummaries: List<DiarySummary>,
        elderFacts: List<ElderFact>,
    ): String = respondWithContext(recentSummaries, elderFacts)

    // ===== 内部渲染 =====

    private fun renderElderFactsBlock(facts: List<ElderFact>): String {
        val sliced = facts.take(MAX_FACTS_INJECTED)
        if (sliced.isEmpty()) return "<elder-facts count=\"0\"></elder-facts>"
        val lines = sliced.joinToString("\n") { fact ->
            "- [${fact.type}, ${fact.confidence}] ${fact.content}"
        }
        return "<elder-facts count=\"${sliced.size}\">\n$lines\n</elder-facts>"
    }

    private fun renderRecentSummariesBlock(summaries: List<DiarySummary>): String {
        val sliced = summaries.take(MAX_SUMMARIES_INJECTED)
        if (sliced.isEmpty()) return "<recent-summaries></recent-summaries>"
        val lines = sliced.joinToString("\n") { s ->
            "- 【${s.date}】${s.summary}"
        }
        return "<recent-summaries>\n$lines\n</recent-summaries>"
    }

    private fun read(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    companion object {
        const val VERSION = "v4"  // v0.10.0 §4 / §5: chat_v1→v2, system_v3→v4

        // 旧版本文件保留（AGENTS.md §11 prompt 版本化要求）：v1 = 0.5.0 基础 / v2 = 0.6.0 加记忆层 / v3 = 0.7.0+ 加粤语 / v4 = 0.10.0+ 删 8 轮硬上限 + 软铺垫场景
        // 注意 SYSTEM_PROMPT_V1 常量名仅是占位（v0.9.0 兼容层用），运行时 main path 走 CHAT_PROMPT (v2)
        private const val SYSTEM_PROMPT_V1 = "agent/system_v4.txt"  // v0.10.0 §5: 兼容层也指向 v4,旧 system_v1/v2/v3 文件保留只读

        // v0.9.0 新增：拆分后的 4 个 prompt 文件
        private const val CHAT_PROMPT = "agent/chat_v2.txt"  // v0.10.0 §4: 粵語強化 + 引導老人說今天
        private const val SAFETY_PROMPT = "agent/safety_v1.txt"
        private const val MEMORY_PROMPT = "agent/memory_v1.txt"
        private const val SAVE_PROMPT = "agent/save_v3.txt"  // SaveAgent 仍用 save_v3（保持兼容）

        /** prd.md §3.1.4 F4：elder-facts 注入上限 = top 50 */
        const val MAX_FACTS_INJECTED = 50

        /** prd.md §3.1.4 F1：recent-summaries N=3 */
        const val MAX_SUMMARIES_INJECTED = 3

        /** prd.md §3.1.4 F1：recent_summaries 总长截断 = 200 字 */
        const val MAX_SUMMARY_CHARS = 200
    }
}
