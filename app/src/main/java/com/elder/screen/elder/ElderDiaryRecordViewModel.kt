// §3.1.2 单次录音 → ASR → 本地日记（v3.0 MVP 唯一核心功能）
package com.elder.android.screen.elder

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.DiaryRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import com.elder.android.util.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class DiaryRecordUiState(
    val isRecording: Boolean = false,
    val elapsedMs: Long = 0,
    val isProcessing: Boolean = false,
    val transcript: String? = null,
    val savedId: Long? = null,
    val topError: String? = null,
    val asrNotConfigured: Boolean = false,
    // PR #4：ASR 上游失败（非 ASR_AUTH_FAILED）时为 true；UI 顶部展示 NetworkYellowBar
    // 对应 prd.md §4.9 黄条规范 — 仅在网络/上游/限流等可重试错误时置位
    val networkFailed: Boolean = false,
)

class ElderDiaryRecordViewModel(app: Application) : AndroidViewModel(app) {
    private val recorder: AudioRecorder = ServiceLocator.audioRecorder
    private val api: AsrApiClient = ServiceLocator.asrApi
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    private val metaRepo: DeviceMetaRepository = ServiceLocator.deviceMetaRepo
    private var emptyRetryCount: Int = 0
    private var recordStartedAt: Long = 0
    // PR #4：保留最近一次录音文件引用，给 retryAsr() 复用。
    // 成功路径走 diaryRepo.audioPath(id) 落库；失败（黄条态）路径保留在内存。
    // 仅做重试，retryAsr() 触发后立即清空（要么成功落库要么被新覆盖）。
    private var lastAudioFile: File? = null

    private val _uiState = MutableStateFlow(DiaryRecordUiState())
    val uiState: StateFlow<DiaryRecordUiState> = _uiState.asStateFlow()

    fun onEnter() {
        viewModelScope.launch {
            asrRepo.current() ?: _uiState.update { it.copy(asrNotConfigured = true) }
            metaRepo.touchActive()
        }
    }

    fun startRecording() {
        try {
            recorder.start()
            recordStartedAt = System.currentTimeMillis()
            // PR #4：开始新录音时清掉 networkFailed / lastAudioFile，旧录音已无可重试性
            lastAudioFile = null
            _uiState.update { it.copy(isRecording = true, elapsedMs = 0, transcript = null, savedId = null, topError = null, networkFailed = false) }
            startElapsedTicker()
        } catch (t: Throwable) {
            _uiState.update { it.copy(topError = "录音没成功，再试一次") }
        }
    }

    private var tickerJob: kotlinx.coroutines.Job? = null
    private fun startElapsedTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                kotlinx.coroutines.delay(200)
                val e = recorder.elapsedMs()
                _uiState.update { it.copy(elapsedMs = e) }
                if (e >= 60_000L) {
                    stopAndProcess()
                    break
                }
            }
        }
    }

    fun stopAndProcess() {
        if (!_uiState.value.isRecording) return
        tickerJob?.cancel()
        _uiState.update { it.copy(isRecording = false, isProcessing = true) }
        val file = recorder.stop()
        if (file == null || !file.exists()) {
            _uiState.update { it.copy(isProcessing = false, topError = "录音没成功，再试一次") }
            return
        }
        processFile(file)
    }

    private fun processFile(file: File) {
        // PR #4：记住 file 以便 retryAsr() 复用；成功路径由 diaryRepo.audioPath(id) 接管
        lastAudioFile = file
        _uiState.update { it.copy(isProcessing = true, networkFailed = false) }
        viewModelScope.launch {
            val cfg = asrRepo.current()
            if (cfg == null || !cfg.isConfigured) {
                file.delete()
                lastAudioFile = null
                _uiState.update { it.copy(isProcessing = false, asrNotConfigured = true, topError = "请先在设置 → AI 语音识别 配置 API") }
                return@launch
            }
            val started = System.currentTimeMillis()
            val outcome = runCatching {
                api.transcribe(
                    apiKey = cfg.apiKey,
                    audioFile = file,
                )
            }
            val elapsed = System.currentTimeMillis() - started

            outcome.onSuccess { result ->
                // 成功：file 已被 diaryRepo.audioPath 接管；lastAudioFile 清空
                lastAudioFile = null
                val text = result.text.trim()
                if (text.isBlank()) {
                    file.delete()
                    emptyRetryCount += 1
                    if (emptyRetryCount >= 3) {
                        emptyRetryCount = 0
                        _uiState.update { it.copy(isProcessing = false, topError = "换个安静点的环境再试") }
                    } else {
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                topError = "没听清，再说一次",
                            )
                        }
                    }
                    return@launch
                }
                emptyRetryCount = 0
                val now = System.currentTimeMillis()
                val durationMs = (now - recordStartedAt).toInt().coerceAtLeast(0)
                val id = diaryRepo.insert(
                    DiaryEntryEntity(
                        deviceId = "local",
                        date = LocalDate.today(),
                        text = text.take(200),
                        transcript = text.take(500),
                        source = DiaryEntryEntity.Source.ASR_ORIGINAL,
                        audioPath = file.absolutePath,
                        durationMs = durationMs,
                        asrProvider = com.elder.android.data.asr.AsrApiClient.BAILIAN_PROVIDER,
                        asrModel = com.elder.android.data.asr.AsrApiClient.BAILIAN_MODEL,
                        asrConfidence = result.confidence,
                        asrLatencyMs = elapsed.toInt(),
                        createdAt = now,
                        updatedAt = now,
                    )
                )
                _uiState.update { it.copy(isProcessing = false, transcript = text, savedId = id) }
            }.onFailure { e ->
                // PR #4：按错误类型分流 — auth 走 topError（用户去设置改 API Key），
                // 其余走 networkFailed + 保留 file 等待 retryAsr() 重试
                if (e is AppError.AsrAuthFailed) {
                    file.delete()
                    lastAudioFile = null
                    _uiState.update { it.copy(isProcessing = false, topError = e.message) }
                } else {
                    // 上游失败 / 限流 / 网络等可重试错误：保留 file，展示黄条
                    val msg = (e as? AppError)?.message ?: e.message ?: "出了点小问题"
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            networkFailed = true,
                            topError = msg,
                        )
                    }
                }
            }
        }
    }

    fun retry() {
        emptyRetryCount = 0
        _uiState.update { it.copy(topError = null, transcript = null, savedId = null) }
        startRecording()
    }

    /**
     * PR #4：黄条「重试」按钮回调。
     * 优先复用最近一次录音文件（ASR 失败时保留的 lastAudioFile）；
     * 文件不在时（用户从其他路径进来、或已被清理）提示重新录音。
     * 对应 prd.md §4.9 网络黄条 + §A.8 ASR 错误映射。
     */
    fun retryAsr() {
        val f = lastAudioFile
        if (f != null && f.exists() && f.length() > 0) {
            _uiState.update { it.copy(networkFailed = false, topError = null) }
            processFile(f)
        } else {
            lastAudioFile = null
            _uiState.update {
                it.copy(
                    networkFailed = false,
                    topError = "请重新录音",
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(topError = null) }
    }

    fun cancel() {
        tickerJob?.cancel()
        recorder.cancel()
        _uiState.update { it.copy(isRecording = false, isProcessing = false) }
    }

    override fun onCleared() {
        super.onCleared()
        recorder.cancel()
    }

    fun playRecording(onStop: () -> Unit) {
        viewModelScope.launch {
            val id = _uiState.value.savedId ?: return@launch
            val path = diaryRepo.audioPath(id) ?: return@launch
            val file = File(path)
            if (!file.exists()) return@launch
            try {
                val player = MediaPlayer()
                player.setDataSource(path)
                player.setOnCompletionListener {
                    it.release()
                    onStop()
                }
                player.prepare()
                player.start()
            } catch (_: Throwable) {
                onStop()
            }
        }
    }
}
