// 对应 docs/v0.11.x-bugfix.md + AGENTS.md §14：InterviewViewModel.saveDiary 状态机安全回归测试。
//
// 修复根因:saveDiary 之前在 `_state.update { stage = SAVED }` 之后才调 `onDone()`,且没有 try-catch
// 兜底。如果 onDone 抛异常 / VM clear 期间 state 已翻 SAVED 但 nav 失败,state 卡死在 SAVED,
// 配合 InterviewScreen 原 LoadingState 全屏 bug,老人看到 "一直显示加载中和进度条"。
//
// 修复后:
//   - `_state.update { stage = SAVED }` 紧跟 `onDone()`(状态先翻再导航,失败时 invokeOnCompletion 兜底)
//   - 导出改成 sibling viewModelScope.launch,不影响 onDone 主流程
//   - launch 整体 invokeOnCompletion 捕获 Throwable,把 state 还原 REVIEW + topError
package com.elder.android.screen.interview

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class InterviewSaveFlowStructuralTest {

    private fun readInterviewViewModel(): String {
        val candidates = listOf(
            "src/main/java/com/elder/screen/interview/InterviewViewModel.kt",
            "app/src/main/java/com/elder/screen/interview/InterviewViewModel.kt",
        )
        return candidates.mapNotNull { runCatching { File(it).readText() }.getOrNull() }
            .firstOrNull() ?: ""
    }

    /**
     * v0.11.x bugfix 行为约束:saveDiary 必须有 invokeOnCompletion 兜底,
     * 不让 state 停在 SAVED(配合 SavingStatusRow 修复,老人不应该在 InterviewScreen
     * 上看不到 transcript + 错误提示)。
     */
    @Test
    fun saveDiary_hasInvokeOnCompletionFallback() {
        val src = readInterviewViewModel()
        check(src.isNotEmpty()) { "无法读取 InterviewViewModel.kt 源码" }
        assertTrue(
            "saveDiary 必须有 .invokeOnCompletion 兜底(对应 docs/v0.11.x-bugfix.md §3.2)",
            src.contains(".invokeOnCompletion"),
        )
        assertTrue(
            "saveDiary 兜底逻辑必须把 stage 还原到 InterviewStage.REVIEW," +
                "而不是停在 SAVED(否则 InterviewScreen 永远显示 SavingStatusRow)",
            src.contains("InterviewStage.REVIEW"),
        )
        assertTrue(
            "saveDiary 兜底必须设 topError 让老人能看到错误提示",
            src.contains("topError = \"保存失败"),
        )
    }

    /**
     * v0.11.x bugfix 行为约束:saveDiary 必须把导出改成 sibling launch(后台 fire-and-forget),
     * 不阻塞 onDone() 主流程。注释里写"不阻塞"但实现里同步 await 是误导 —— 必须有
     * 独立的 `launch { runCatching { saveExportRepo.exportIfNeeded(...) } }` 结构。
     */
    @Test
    fun saveDiary_runsExportInSiblingLaunch() {
        val src = readInterviewViewModel()
        check(src.isNotEmpty()) { "无法读取 InterviewViewModel.kt 源码" }
        // 检查 saveExportRepo.exportIfNeeded 被包在 runCatching 里(说明是 sibling launch)
        assertTrue(
            "saveDiary 必须把 saveExportRepo.exportIfNeeded 调用包在 runCatching 里," +
                "否则 sibling launch 内抛异常会污染外层 coroutine",
            src.contains("runCatching") &&
                Regex("runCatching\\s*\\{[^}]*saveExportRepo\\.exportIfNeeded", RegexOption.DOT_MATCHES_ALL)
                    .containsMatchIn(src),
        )
    }

    /**
     * v0.11.x bugfix 行为约束:onEnter 必须拆 onEnterInternal + try-catch 兜底,
     * 抛异常时强制翻 READY + topError,不让 PREPARING 阶段霸屏。
     */
    @Test
    fun onEnter_hasTryCatchRecoveredIntoReady() {
        val src = readInterviewViewModel()
        check(src.isNotEmpty()) { "无法读取 InterviewViewModel.kt 源码" }
        assertTrue(
            "onEnter 必须拆出 onEnterInternal 让 try-catch 包裹更精准",
            src.contains("fun onEnterInternal()"),
        )
        assertTrue(
            "onEnter 抛异常 catch 后必须有 '初始化失败' 文案 + topError 字段," +
                "强制翻 InterviewStage.READY,不再让 PREPARING 霸屏",
            src.contains("初始化失败") && src.contains("topError"),
        )
    }
}
