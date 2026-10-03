// 对应 prd.md §3.1.2：录制页「已录音」状态下的本地语音回听。
//
// 设计要点（沿用 docs/v0.11.x-bugfix.md §A.18 DiaryDetailViewModel 既有 lock）：
//   1. ticker 循环用 tickerActive 标志，不用 mp.isPlaying 判定，避免读取 End-state MediaPlayer 抛 IllegalStateException
//   2. ticker 整体 runCatching 兜底，异常仅退出循环不向上抛（viewModelScope.launch 未捕获异常会 crash app）
//   3. setOnPreparedListener.start() / setOnCompletionListener body 全部 runCatching 兜底
//   4. stopInternal 三步：取 local mp → 立即置空字段 → release，防止 ticker 持有 stale 引用再次 release
//
// 不引入新上游 SDK（仅 MediaPlayer，无 ExoPlayer/无 MediaSession）。
// 不动 §18 既有 lock；不破坏 Realtime ASR frameListener 契约。
//
// 抽到独立文件的理由：AGENTS.md §3 单文件 ≤400 行；ElderDiaryRecordViewModel 已 375 行,
// 塞下 togglePlay/startTicker/stopInternal 等 ~80 行会触发 §18 拆分，所以委派到这里。
package com.elder.android.screen.elder

import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 录制页「已录音」状态下的语音回听委派。
 *
 * 调用方（[ElderDiaryRecordViewModel]）只需：
 * - 调 [togglePlay] 触发播放/暂停
 * - 在 onCleared() 调 [release]
 *
 * 线程模型：所有 MediaPlayer listener 都在主线程（MediaPlayer 设计如此），
 * 所以 onState / onError 回调也会在主线程；调用方做 _uiState.update 无需切线程。
 */
internal class RecordPlaybackDelegate {

    @Volatile private var mediaPlayer: MediaPlayer? = null
    @Volatile private var tickerActive: Boolean = false
    @Volatile private var currentPath: String? = null

    /**
     * 播放/暂停切换。
     *
     * @param path 录音文件绝对路径
     * @param isCurrentlyPlaying 当前 UI 上的 isPlaying（用于切暂停）
     * @param onState 状态变化回调（isPlaying / playheadMs / totalMs）
     * @param onError 错误回调（错误文案；调用方写到 topError 或 playError）
     * @param tickerScope 提供 viewModelScope 协程作用域；这里只接 CoroutineScope 接口以保持 delegate 不依赖 viewModelScope
     */
    fun togglePlay(
        path: String,
        isCurrentlyPlaying: Boolean,
        onState: (playing: Boolean, head: Long, total: Long) -> Unit,
        onError: (String) -> Unit,
        tickerScope: CoroutineScope,
    ) {
        if (isCurrentlyPlaying) {
            stopInternal()
            onState(false, 0L, 0L)
            return
        }
        runCatching {
            stopInternal()
            val mp = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener {
                    val started = runCatching { it.start() }
                    if (started.isFailure) {
                        Log.w(TAG, "MediaPlayer.start() failed: ${started.exceptionOrNull()?.message}")
                        onError(started.exceptionOrNull()?.message ?: "无法开始播放")
                        stopInternal()
                        return@setOnPreparedListener
                    }
                    onState(true, 0L, it.duration.toLong())
                    startTicker(it, onState, tickerScope)
                }
                setOnCompletionListener {
                    runCatching {
                        stopInternal()
                        onState(false, 0L, 0L)
                    }.onFailure { e ->
                        Log.w(TAG, "onCompletion handler failed: ${e.message}")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    onError("MediaPlayer 错误($what/$extra)")
                    stopInternal()
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
            currentPath = path
        }.onFailure { e ->
            onError(e.message ?: "无法播放")
        }
    }

    /**
     * ticker：用 tickerActive 标志而非 mp.isPlaying 判定循环，避免 End-state MediaPlayer 抛
     * IllegalStateException。currentPosition 也 runCatching，任意 MediaPlayer 状态异常
     * → 退出循环，不向上抛。
     */
    private fun startTicker(
        mp: MediaPlayer,
        onState: (playing: Boolean, head: Long, total: Long) -> Unit,
        tickerScope: CoroutineScope,
    ) {
        tickerActive = true
        tickerScope.launch {
            while (tickerActive) {
                val read = runCatching {
                    onState(true, mp.currentPosition.toLong(), mp.duration.toLong())
                }
                if (read.isFailure) {
                    Log.w(TAG, "ticker currentPosition failed: ${read.exceptionOrNull()?.message}")
                    tickerActive = false
                    break
                }
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    /**
     * stopInternal 三步：取 local 引用 → 立即置空字段 → release。
     * 防止 ticker 通过 stale mediaPlayer 字段再次 release 同一 MediaPlayer
     * （stop()/release() 在 End-state 上抛 IllegalStateException）。
     * stop() 仅在 isPlaying 时调（PlaybackCompleted 状态 stop() 部分厂商会抛）。
     */
    private fun stopInternal() {
        val mp = mediaPlayer ?: return
        mediaPlayer = null
        currentPath = null
        tickerActive = false
        runCatching { if (mp.isPlaying) mp.stop() }
        runCatching { mp.release() }
    }

    /** 当前已保存路径（用于 VM 判断「还在播这次的录音吗」） */
    fun currentAudioPath(): String? = currentPath

    /** 释放 MediaPlayer；onCleared() / cancel() 调用 */
    fun release() {
        stopInternal()
    }

    private companion object {
        const val TAG = "RecordPlayback"
    }
}
