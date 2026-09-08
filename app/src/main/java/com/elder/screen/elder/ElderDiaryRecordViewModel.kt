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
)

class ElderDiaryRecordViewModel(app: Application) : AndroidViewModel(app) {
    private val recorder: AudioRecorder = ServiceLocator.audioRecorder
    private val api: AsrApiClient = ServiceLocator.asrApi
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    private val metaRepo: DeviceMetaRepository = ServiceLocator.deviceMetaRepo
    private var emptyRetryCount: Int = 0
    private var recordStartedAt: Long = 0

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
            _uiState.update { it.copy(isRecording = true, elapsedMs = 0, transcript = null, savedId = null, topError = null) }
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
        viewModelScope.launch {
            val cfg = asrRepo.current()
            if (cfg == null || !cfg.isConfigured) {
                file.delete()
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
                file.delete()
                val msg = (e as? AppError)?.message ?: e.message ?: "出了点小问题"
                _uiState.update { it.copy(isProcessing = false, topError = msg) }
            }
        }
    }

    fun retry() {
        emptyRetryCount = 0
        _uiState.update { it.copy(topError = null, transcript = null, savedId = null) }
        startRecording()
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
