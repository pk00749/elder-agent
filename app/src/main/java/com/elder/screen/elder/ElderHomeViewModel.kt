// §3.1.5 老人端主屏（v3.0 MVP）ViewModel
// PR #2：删除 5 次点击隐藏入口（tapCounter / settingsTrigger / todayRecordJustRecorded
// 字段，onGreetingTap / consumeSettingsTrigger / consumeTodayRecordFlag 方法）；设置入口
// 改为可见按钮（UI 直接调 onOpenSettings 回调），不再需要 ViewModel 兜底
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.di.ServiceLocator
import com.elder.android.util.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 顶层函数：供 data class default value 使用（companion object 静态方法在 default value 不可达）
private fun nowHour(): Int =
    SimpleDateFormat("HH", Locale.US).format(Date()).toIntOrNull() ?: 9

private fun greetingForHour(h: Int): String = when {
    h < 5 -> "夜深了"
    h < 11 -> "早上好"
    h < 13 -> "中午好"
    h < 18 -> "下午好"
    else -> "晚上好"
}

private fun formatToday(): String =
    SimpleDateFormat("M 月 d 日 EEE HH:mm", Locale.CHINA).format(Date())

data class ElderHomeUiState(
    val greeting: String = greetingForHour(nowHour()),
    val dateLine: String = formatToday(),
    val todayRecorded: Boolean = false,
    val showAsrHint: Boolean = false,
)

class ElderHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val _uiState = MutableStateFlow(ElderHomeUiState())
    val uiState: StateFlow<ElderHomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ServiceLocator.deviceMetaRepo.ensureInitialized()
            combineState()
        }
    }

    private fun combineState() {
        viewModelScope.launch {
            asrRepo.observe().collect { cfg ->
                _uiState.update { it.copy(showAsrHint = cfg == null) }
            }
        }
        viewModelScope.launch {
            diaryRepo.observeAll().collect { _ ->
                val today = LocalDate.today()
                val has = diaryRepo.hasAnyOnDate(today)
                _uiState.update { it.copy(todayRecorded = has) }
            }
        }
    }
}
