// 对应 PRD §3.1.4 F1 数据源 1：从 DiaryEntryEntity 读取最近 N 天 summary，注入 <recent-summaries>。
// v0.6.0 新增；N=3 hardcoded（AGENTS.md §A.11 范围内最小记忆原则）。
package com.elder.android.data

import com.elder.android.agent.DiarySummary
import com.elder.android.data.db.DiaryDao
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.util.LocalDate
import kotlinx.coroutines.flow.first
import java.time.LocalDate as JavaLocalDate

class RecentSummaryLoader(
    private val diaryDao: DiaryDao,
    /** §F1 hardcoded N=3，含当天 */
    private val days: Int = 3,
    /** §F1 summary 总长截断 200 字（按 E1 ~10 字 × 3 天 + 余量估） */
    private val maxTotalChars: Int = 200,
) {

    /**
     * 拉最近 N 天有 summary 的日记；按 date 倒序（含当天）。
     * 列表总长超 [maxTotalChars] 时按日期倒序截断最早的。
     *
     * @param todayDate "YYYY-MM-DD" 字符串（与 DiaryEntryEntity.date 字段同格式）
     */
    suspend fun loadRecentSummaries(todayDate: String): List<DiarySummary> {
        val today = JavaLocalDate.parse(todayDate)
        val start = today.minusDays((days - 1).toLong())
        val startDate = start.toString()
        val rows: List<DiaryEntryEntity> = diaryDao.observeByDateRange(
            startDate = startDate,
            endDate = todayDate,
        ).first()
        val withSummary = rows.filter { !it.summary.isNullOrBlank() }
        val sorted = withSummary.sortedByDescending { it.date }

        // 倒序累计截断
        val result = mutableListOf<DiarySummary>()
        var totalChars = 0
        for (entity in sorted) {
            val s = entity.summary ?: continue
            val projected = totalChars + s.length
            if (projected > maxTotalChars && result.isNotEmpty()) {
                break
            }
            result.add(DiarySummary(date = entity.date, summary = s))
            totalChars = projected
        }
        return result
    }

    companion object {
        fun get(context: android.content.Context): RecentSummaryLoader =
            RecentSummaryLoader(com.elder.android.data.db.ElderDatabase.get(context).diaryDao())
    }
}
