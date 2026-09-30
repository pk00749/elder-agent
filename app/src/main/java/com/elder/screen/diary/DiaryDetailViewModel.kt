// 对应 docs/v0.10.0.md §7：DiaryDetailViewModel。
// 加载单条 diary_entry_local + 提供播放状态；不引入新 SDK；仅 Room + MediaPlayer。
package com.elder.android.screen.diary

import android.app.Application
import android.media.MediaPlayer
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
                    it.start()
                    _uiState.update { st ->
                        st.copy(isPlaying = true, totalMs = it.duration.toLong(), playheadMs = 0L)
                    }
                    startTicker(it)
                }
                setOnCompletionListener {
                    stopInternal()
                    _uiState.update { it.copy(isPlaying = false, playheadMs = 0L) }
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

    private fun startTicker(mp: MediaPlayer) {
        viewModelScope.launch {
            while (mp.isPlaying) {
                _uiState.update { it.copy(playheadMs = mp.currentPosition.toLong()) }
                kotlinx.coroutines.delay(200L)
            }
        }
    }

    private fun stopInternal() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
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
