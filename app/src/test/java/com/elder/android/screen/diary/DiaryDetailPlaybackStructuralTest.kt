// 对应 docs/v0.11.x-bugfix.md (DiaryDetail 闪退修复) + AGENTS.md §14 / §18：
// DiaryDetailViewModel 回听录音播放完成后偶发闪退的回归测试。
//
// 根因:MediaPlayer.setOnCompletionListener 触发时 ticker 协程仍在 200ms 轮询;
// stopInternal() 已 release MediaPlayer 到 End state,ticker 下次 delay 唤醒后调
// mp.isPlaying / mp.currentPosition 会抛 IllegalStateException;未捕获异常传到
// viewModelScope.launch → 主线程 uncaughtExceptionHandler → 闪退。
// 此外 setOnPreparedListener.it.start() 不在 runCatching 内,listener 抛异常 → crash。
//
// 锁定行为(源码扫描):
//   1. tickerActive 标志必须存在,且在 startTicker / stopInternal 中读写;
//   2. startTicker 内部 while 循环必须用 tickerActive,不再依赖 mp.isPlaying;
//   3. mp.currentPosition 调用必须套 runCatching;
//   4. setOnPreparedListener.it.start() 必须套 runCatching;
//   5. setOnCompletionListener 整个 body 必须套 runCatching;
//   6. stopInternal 必须"先取 local 引用 → 立即置空 mediaPlayer 字段 → release",
//      且 stop() 调用前先判 mp.isPlaying(PlaybackCompleted 状态部分厂商 stop() 会抛)。
package com.elder.android.screen.diary

import com.elder.android.testing.ElderRobolectricTestRunner
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(ElderRobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DiaryDetailPlaybackStructuralTest {

    private fun readDiaryDetailViewModel(): String {
        val candidates = listOf(
            "src/main/java/com/elder/screen/diary/DiaryDetailViewModel.kt",
            "app/src/main/java/com/elder/screen/diary/DiaryDetailViewModel.kt",
        )
        return candidates.mapNotNull { runCatching { File(it).readText() }.getOrNull() }
            .firstOrNull() ?: ""
    }

    /**
     * v0.11.x bugfix:tickerActive 标志必须存在且为 @Volatile,
     * 用于取代 `mp.isPlaying` 作为 ticker 循环判断 —— End-state MediaPlayer 上
     * `isPlaying()` 在不同 Android 版本行为不一致(返回 false / 抛 IllegalStateException),
     * 是导致 ticker 协程未捕获异常闪退的直接路径。
     */
    @Test
    fun tickerActive_fieldExists_andIsVolatile() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        assertTrue(
            "DiaryDetailViewModel 必须有 tickerActive 字段(v0.11.x bugfix:ticker 循环开关,替代 mp.isPlaying)",
            Regex("@Volatile\\s+private\\s+var\\s+tickerActive\\s*:\\s*Boolean").containsMatchIn(src),
        )
    }

    /**
     * v0.11.x bugfix:ticker 循环必须用 tickerActive 而不是 mp.isPlaying。
     * 锁定:`startTicker` 内的 while 条件是 `tickerActive`,且不能再出现 `mp.isPlaying`。
     */
    @Test
    fun startTicker_loopUsesTickerActive_notMpIsPlaying() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        assertTrue(
            "startTicker 必须使用 tickerActive 作为循环条件(v0.11.x bugfix §1)",
            Regex("while\\s*\\(\\s*tickerActive\\s*\\)\\s*\\{").containsMatchIn(src),
        )
        val startTickerBlock = Regex(
            "private\\s+fun\\s+startTicker[\\s\\S]*?(?=\\n\\s*(?:private|override|fun))",
        ).find(src)?.value ?: ""
        assertTrue(
            "无法定位 startTicker 函数体源码,可能函数签名被改",
            startTickerBlock.isNotEmpty(),
        )
        assertTrue(
            "startTicker 内不允许调用 mp.isPlaying(End-state MediaPlayer 会抛 IllegalStateException,导致 ticker 协程未捕获闪退)",
            !startTickerBlock.contains("mp.isPlaying"),
        )
    }

    /**
     * v0.11.x bugfix:ticker 调用 mp.currentPosition 必须套 runCatching,
     * 否则 End-state MediaPlayer 上的 currentPosition() 抛出未捕获异常 → 闪退。
     */
    @Test
    fun ticker_currentPosition_isWrappedInRunCatching() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        assertTrue(
            "startTicker 内必须用 runCatching 包裹 mp.currentPosition 调用(v0.11.x bugfix §2)",
            Regex(
                "runCatching\\s*\\{[\\s\\S]*?mp\\.currentPosition[\\s\\S]*?\\}",
                RegexOption.DOT_MATCHES_ALL,
            ).containsMatchIn(src),
        )
    }

    /**
     * v0.11.x bugfix:setOnPreparedListener 内的 it.start() 必须套 runCatching。
     * listener 在主线程异步执行,异常逃逸会 crash app(runCatching 只保护同步块)。
     */
    @Test
    fun onPrepared_start_isWrappedInRunCatching() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        val block = Regex(
            "setOnPreparedListener\\s*\\{([\\s\\S]*?)setOnCompletionListener",
            RegexOption.DOT_MATCHES_ALL,
        ).find(src)?.groupValues?.get(1) ?: ""
        assertTrue("无法定位 setOnPreparedListener lambda 源码", block.isNotEmpty())
        val hasRunCatchingStart = Regex(
            "runCatching\\s*\\{[\\s\\S]*?\\.start\\(\\)[\\s\\S]*?\\}",
            RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(block)
        assertTrue(
            "setOnPreparedListener 内 .start() 必须被 runCatching 包裹(v0.11.x bugfix §4)",
            hasRunCatchingStart,
        )
    }

    /**
     * v0.11.x bugfix:setOnCompletionListener 整个 body 必须套 runCatching,
     * 否则 stopInternal 之外的 state update 等可能抛(虽然 stopInternal 自身 runCatching)。
     */
    @Test
    fun onCompletion_body_isWrappedInRunCatching() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        val block = Regex(
            "setOnCompletionListener\\s*\\{([\\s\\S]*?)setOnErrorListener",
            RegexOption.DOT_MATCHES_ALL,
        ).find(src)?.groupValues?.get(1) ?: ""
        assertTrue("无法定位 setOnCompletionListener lambda 源码", block.isNotEmpty())
        assertTrue(
            "setOnCompletionListener body 必须被 runCatching 包裹(v0.11.x bugfix §4)",
            block.contains("runCatching {"),
        )
    }

    /**
     * v0.11.x bugfix:stopInternal 必须先取 local 引用 → 立即置空 mediaPlayer → release,
     * 防止 ticker 通过 stale 引用再次 release 同一 MediaPlayer(stop()/release()
     * 在 End-state 上抛 IllegalStateException)。
     */
    @Test
    fun stopInternal_localReferenceBeforeRelease() {
        val src = readDiaryDetailViewModel()
        check(src.isNotEmpty()) { "无法读取 DiaryDetailViewModel.kt 源码" }
        val stopBlock = Regex(
            "private\\s+fun\\s+stopInternal\\(\\)\\s*\\{([\\s\\S]*?\\n\\s*\\})",
        ).find(src)?.groupValues?.get(1) ?: ""
        assertTrue("无法定位 stopInternal 函数体", stopBlock.isNotEmpty())
        assertTrue(
            "stopInternal 必须先取 local 引用再置空(v0.11.x bugfix §3):" +
                "'val mp = mediaPlayer' 早于 'mediaPlayer = null'",
            stopBlock.indexOf("val mp = mediaPlayer") >= 0 &&
                stopBlock.indexOf("val mp = mediaPlayer") < stopBlock.indexOf("mediaPlayer = null"),
        )
        assertTrue(
            "stopInternal 必须显式置空 mediaPlayer 字段,防止 stale 引用再次 release",
            stopBlock.contains("mediaPlayer = null"),
        )
        assertTrue(
            "stopInternal 内 stop() 必须先判 isPlaying(PlaybackCompleted 状态 stop() 部分厂商会抛)",
            stopBlock.contains("if (mp.isPlaying) mp.stop()"),
        )
        assertTrue(
            "stopInternal 必须调 mp.release()(转让 End-state,GC 回收 native 资源)",
            stopBlock.contains("mp.release()"),
        )
    }
}
