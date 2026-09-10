// §3.1.7 老人端「今日记录」时间轴屏（v3.0 MVP 版）
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.DiaryRepository
import com.elder.android.di.ServiceLocator
import com.elder.android.util.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DiaryRecentRange { TODAY, LAST_7_DAYS }

data class DiaryRecentUiState(
    val range: DiaryRecentRange = DiaryRecentRange.TODAY,
    val entries: List<DiaryEntryEntity> = emptyList(),
    val editingId: Long? = null,
    val editingDraft: String = "",
)

class ElderDiaryRecentViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: DiaryRepository = ServiceLocator.diaryRepo
    private val _uiState = MutableStateFlow(DiaryRecentUiState())
    val uiState: StateFlow<DiaryRecentUiState> = _uiState.asStateFlow()

    init {
        observe()
    }

    fun setRange(r: DiaryRecentRange) {
        _uiState.update { it.copy(range = r) }
        observe()
    }

    private fun observe() {
        viewModelScope.launch {
            val range = _uiState.value.range
            val flow = when (range) {
                DiaryRecentRange.TODAY -> repo.observeByDate(LocalDate.today())
                DiaryRecentRange.LAST_7_DAYS -> {
                    val cal = Calendar.getInstance()
                    val end = LocalDate.today()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    val start = LocalDate.format(cal.timeInMillis)
                    repo.observeByDateRange(start, end)
                }
            }
            flow.collect { list -> _uiState.update { it.copy(entries = list) } }
        }
    }

    fun startEdit(id: Long, current: String) {
        _uiState.update { it.copy(editingId = id, editingDraft = current) }
    }

    fun updateDraft(v: String) {
        _uiState.update { it.copy(editingDraft = v.take(200)) }
    }

    fun saveEdit(onDone: () -> Unit = {}) {
        val s = _uiState.value
        val id = s.editingId ?: return
        viewModelScope.launch {
            repo.updateText(id, s.editingDraft.trim(), System.currentTimeMillis())
            _uiState.update { it.copy(editingId = null, editingDraft = "") }
            onDone()
        }
    }

    fun cancelEdit() {
        _uiState.update { it.copy(editingId = null, editingDraft = "") }
    }

    /**
     * PR #4：空态 ▶ TTS 按钮回调（对应 prd.md §4.5 千问 TTS + §4.7 空态）。
     * 当前 MVP 范围：老人端未接入 TTS SDK，本方法为占位；正式实现将调用
     * elder_common.tts.synthesize(text) 并走千问粤语男声（§A.1 TTS 封装）。
     * ViewModel 层只暴露入口；UI 不直接持有 SDK。
     */
    fun ttsPlay(text: String) {
        // 占位：MVP 老人端 TTS 推迟到接入千问 SDK 时实现。
        // 保留签名便于 ElderEmptyState 组件直接回调，避免 UI 改 schema。
    }
}
