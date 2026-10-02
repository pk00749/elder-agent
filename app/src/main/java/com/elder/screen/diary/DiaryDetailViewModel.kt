// 对应 docs/v0.10.0.md §7：DiaryDetailViewModel。
// 加载单条 diary_entry_local + 提供播放状态；不引入新 SDK；仅 Room + MediaPlayer。
//
// v0.11.x bugfix (本页)：回听录音播放完成后偶发闪退的根因修复。
//   根因:MediaPlayer.setOnCompletionListener 触发时 ticker 协程仍在 200ms 轮询,
//   stopInternal() 已 release MediaPlayer 到 End state,ticker 下次 delay 唤醒后调
//   mp.isPlaying / mp.currentPosition 会抛 IllegalStateException;未捕获异常传到
//   viewModelScope.launch → 主线程 uncaughtExceptionHandler → 闪退。
//   此外 setOnPreparedListener.it.start() 不在 runCatching 内,若 prepareAsync 与
//   completion/error 之间 race 也会抛 IllegalStateException 致闪退。
//   修复:
//     1. 引入 tickerActive 标志替代 mp.isPlaying 判断循环;ticker 不再读 End-state MediaPlayer;
//     2. ticker 整体 try-catch 兜底,异常仅退出循环不向上抛;
//     3. stopInternal 先取 local mp 引用 → 立即置空 mediaPlayer 字段 → release;
//        防止 ticker 持有 stale 引用再次 release;
//     4. setOnPreparedListener.it.start() / setOnCompletionListener body 套 runCatching;
//     5. setOnErrorListener 已经在监听器内兜底,不动。
//   不动:§5 数据模型字段 / §3.1.4 A/B/D 硬约束 / §A.16.6 token 联动 / §18 既有 lock;
//   行数 137 → 208,仍 < 400(AGENTS.md §3)。
package com.elder.android.screen.diary

import android.app.Application
import android.media.MediaPlayer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.DiaryRepository
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DiaryDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    @Volatile private var mediaPlayer: MediaPlayer? = null
    /**
     * v0.11.x bugfix:ticker 循环开关,与 MediaPlayer 实例生命周期脱钩。
     * setOnCompletionListener / stopInternal 都会置 false;ticker 不再依赖
     * `mp.isPlaying`(End-state MediaPlayer 上可能抛 IllegalStateException)。
     */
    @Volatile private var tickerActive: Boolean = false

    private val _uiState = MutableStateFlow(DiaryDetailUiState())
    val uiState: StateFlow<DiaryDetailUiState> = _uiState.asStateFlow()

    fun load(diaryId: Long) {
        viewModelScope.launch {
            val entry = diaryRepo.findById(diaryId) ?: return@launch
            _uiState.update {
                it.copy(
                    loaded = true,
                    diary = entry,
                    title = extractTitle(entry),
                    dateLine = formatDateLine(entry),
                    charCount = entry.text.length,
                    isExpanded = false,
                )
            }
        }
    }

    fun toggleExpand() = _uiState.update { it.copy(isExpanded = !it.isExpanded) }

    /**
     * 播放/暂停现有录音；失败写 error；结束时 reset state。
     *
     * v0.11.x bugfix:setOnPreparedListener.it.start() 与 setOnCompletionListener 内部 body
     * 都套 runCatching —— MediaPlayer listener 在主线程执行且不在外层 runCatching 范围内,
     * 异常逃逸会直接 crash app(runCatching 只保护构造 MediaPlayer 时的同步块)。
     */
    fun togglePlay() {
        val s = _uiState.value
        val path = s.diary?.audioPath ?: return
        if (s.isPlaying) {
            stopInternal()
            _uiState.update { it.copy(isPlaying = false, playheadMs = 0L) }
            return
        }
        runCatching {
            stopInternal()
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    // v0.11.x bugfix:start() 抛 IllegalStateException 不应逃出 listener。
                    // 场景:prepareAsync 成功 → 完成回调几乎同时触发 → mp 进 Error/End state →
                    // start() 抛 IllegalStateException,无 runCatching 兜底则 crash。
                    val started = runCatching { it.start() }
                    if (started.isFailure) {
                        Log.w(TAG, "MediaPlayer.start() failed: ${started.exceptionOrNull()?.message}")
                        _uiState.update { st ->
                            st.copy(isPlaying = false, playError = started.exceptionOrNull()?.message ?: "无法开始播放")
                        }
                        stopInternal()
                        return@setOnPreparedListener
                    }
                    _uiState.update { st ->
                        st.copy(isPlaying = true, totalMs = it.duration.toLong(), playheadMs = 0L)
                    }
                    startTicker(it)
                }
                setOnCompletionListener {
                    // v0.11.x bugfix:整个 body 套 runCatching(stopInternal 自身已 runCatching,
                    // 但 stopInternal 之外的 state update 等仍可能抛)。
                    runCatching {
                        stopInternal()
                        _uiState.update { it.copy(isPlaying = false, playheadMs = 0L) }
                    }.onFailure { e ->
                        Log.w(TAG, "onCompletion handler failed: ${e.message}")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    _uiState.update { it.copy(isPlaying = false, playError = "MediaPlayer 错误($what/$extra)") }
                    stopInternal()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        }.onFailure { e ->
            _uiState.update { it.copy(isPlaying = false, playError = e.message ?: "无法播放") }
        }
    }

    /**
     * v0.11.x bugfix:ticker 用 tickerActive 标志而非 `mp.isPlaying` 判定循环,避免读取
     * End-state MediaPlayer 抛 IllegalStateException。currentPosition 也套 runCatching,
     * 任何 MediaPlayer 状态异常 → 退出循环,不让 viewModelScope 协程未捕获 crash。
     */
    private fun startTicker(mp: MediaPlayer) {
        tickerActive = true
        viewModelScope.launch {
            while (tickerActive) {
                val read = runCatching {
                    _uiState.update { it.copy(playheadMs = mp.currentPosition.toLong()) }
                }
                if (read.isFailure) {
                    // 任意 MediaPlayer 状态异常(End/Error/未准备) → 退出 ticker,不向上抛。
                    Log.w(TAG, "ticker currentPosition failed: ${read.exceptionOrNull()?.message}")
                    tickerActive = false
                    break
                }
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    /**
     * v0.11.x bugfix:stopInternal 改成"取 local 引用 → 立即置空 → release"三步,避免
     * ticker 通过 stale mediaPlayer 字段再次 release 同一 MediaPlayer(stop()/release()
     * 在 End-state 上抛 IllegalStateException)。stop() 仅在 isPlaying 时调(PlaybackCompleted
     * 状态 stop() 部分厂商实现会抛,即使 runCatching 兜住也浪费一次 native 调用)。
     */
    private fun stopInternal() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        tickerActive = false
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.release() }
    }

    override fun onCleared() {
        super.onCleared()
        stopInternal()
    }

    private fun extractTitle(entry: DiaryEntryEntity): String {
        val firstLine = entry.text.lineSequence().firstOrNull()?.trim().orEmpty()
        return firstLine.ifEmpty { "日志" }
    }

    private fun formatDateLine(entry: DiaryEntryEntity): String {
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = entry.createdAt
        }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val day = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = cal.get(java.util.Calendar.MINUTE)
        return "${month}月${day}日 ${"%02d".format(hour)}:${"%02d".format(minute)}"
    }

    private companion object {
        const val TAG = "DiaryDetailVM"
    }
}

data class DiaryDetailUiState(
    val loaded: Boolean = false,
    val diary: DiaryEntryEntity? = null,
    val title: String = "日志",
    val dateLine: String = "",
    val charCount: Int = 0,
    val isExpanded: Boolean = false,
    val isPlaying: Boolean = false,
    val playheadMs: Long = 0L,
    val totalMs: Long = 0L,
    val playError: String? = null,
) {
    /** 转写文本是否需要 6 行 clamp(超过 6 行才显示 mask + 展开按钮)。 */
    val shouldClampTranscript: Boolean
        get() = (diary?.transcript?.length ?: 0) > 60

    val progressFraction: Float
        get() = if (totalMs <= 0L) 0f else (playheadMs.toFloat() / totalMs).coerceIn(0f, 1f)
}
