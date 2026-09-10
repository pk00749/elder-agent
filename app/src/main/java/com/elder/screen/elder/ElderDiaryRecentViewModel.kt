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
}
