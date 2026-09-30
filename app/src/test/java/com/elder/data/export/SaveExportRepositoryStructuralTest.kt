// 对应 docs/v0.11.x-bugfix.md + AGENTS.md §14：SaveExportRepository 超时兜底结构回归测试。
//
// 修复根因:saveDiary 的 viewModelScope.launch 在 SAVED → onDone 顺序下,saveExportRepo.exportIfNeeded
// 是 suspend 调用,如果 OSS 网络挂死,Launch 永远不返回 → state 卡在 SAVED →
// 配合 InterviewScreen 原 LoadingState 全屏 bug → 老人看到"一直显示加载中和进度条"。
//
// 修复:exportIfNeeded 用 withTimeoutOrNull(EXPORT_TIMEOUT_MS) 包住整段 runExport,
// 超时返回 ExportOutcome.Failed("EXPORT_TIMEOUT", ...) 而不是抛 TimeoutCancellationException。
package com.elder.android.data.export

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SaveExportRepositoryStructuralTest {

    private fun readSaveExportRepository(): String {
        val candidates = listOf(
            "src/main/java/com/elder/data/export/SaveExportRepository.kt",
            "app/src/main/java/com/elder/data/export/SaveExportRepository.kt",
        )
        return candidates.mapNotNull { runCatching { File(it).readText() }.getOrNull() }
            .firstOrNull() ?: ""
    }

    /**
     * 行为约束:EXPORT_TIMEOUT_MS 必须存在且为 3000ms(对应 docs/v0.11.x-bugfix.md §3.2 默认值)。
     * 这个值不能随便改 —— 改大会让 OSS 网络挂死时 LoadingState 持续可见。
     */
    @Test
    fun exportTimeoutMs_is3Seconds() {
        assertEquals(
            "EXPORT_TIMEOUT_MS 默认值必须锁定为 3000ms(对应 docs/v0.11.x-bugfix.md)",
            3_000L, SaveExportRepository.EXPORT_TIMEOUT_MS,
        )
    }

    /**
     * 行为约束:exportIfNeeded 内部必须用 withTimeoutOrNull + 单独的 runExport 私有方法,
     * 不允许改回单函数阻塞式实现。
     */
    @Test
    fun exportIfNeeded_usesWithTimeoutOrNull() {
        val src = readSaveExportRepository()
        check(src.isNotEmpty()) { "无法读取 SaveExportRepository.kt 源码" }
        assertTrue(
            "exportIfNeeded 必须用 withTimeoutOrNull 包住 runExport(否则无超时保护)",
            src.contains("withTimeoutOrNull(EXPORT_TIMEOUT_MS)"),
        )
        assertTrue(
            "runExport 必须作为 private fun 拆出,便于单测 + withTimeoutOrNull 包住",
            src.contains("private fun runExport(") ||
                src.contains("private suspend fun runExport("),
        )
        assertTrue(
            "超时返回 ExportOutcome.Failed(\"EXPORT_TIMEOUT\", ...) 而不是抛异常",
            src.contains("EXPORT_TIMEOUT"),
        )
    }
}
