package com.elder.android.agent

/**
 * 对应 prd.md §3.1.4 F1 / F4；v0.6.0 新增。
 * Agent 层 DTO，与 Room Entity 解耦，避免 agent 包反向依赖 data.db 包。
 * 由 RecentSummaryLoader（data 包）/ ElderFactRepository（data.db 包）映射而来。
 */

/** 最近 N 天日记的 summary 注入；用于 <recent-summaries> 块。 */
data class DiarySummary(
    val date: String,      // YYYY-MM-DD，按设备本地时区
    val summary: String,   // ≤60 汉字的 Agent 整理摘要
)

/** 长期事实注入；用于 <elder-facts> 块。 */
data class ElderFact(
    val id: String,        // UUID
    val type: String,      // person | place | event | preference | health
    val confidence: String, // high | medium | low
    val content: String,   // ≤100 汉字
    val mentionCount: Int = 0,
    val lastUsedAt: Long = 0L,
)
