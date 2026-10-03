// §3.1.2 单次录音 → Realtime ASR → 本地日记（v3.0 MVP 唯一核心功能）
package com.elder.android.screen.elder

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.asr.RealtimeAsrSession
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.di.ServiceLocator
import com.elder.android.error.AppError
import com.elder.android.util.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val networkFailed: Boolean = false,
    // 录音音量（readLoop 回调）;已录音状态下回听进度
    val audioLevel: Float = 0f,
    val isPlaying: Boolean = false,
    val playheadMs: Long = 0L,
    val totalMs: Long = 0L,
    val playError: String? = null,
)

class ElderDiaryRecordViewModel(app: Application) : AndroidViewModel(app) {
    private val recorder: AudioRecorder = ServiceLocator.audioRecorder
    private val api: AsrApiClient = ServiceLocator.asrApi  // 兜底默认 Provider=bailian；运行时按 cfg.asrProvider 解析
    private val asrRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    private val metaRepo: DeviceMetaRepository = ServiceLocator.deviceMetaRepo
    private var lastAudioFile: File? = null
    private var asrSession: RealtimeAsrSession? = null
    private var tickerJob: Job? = null
    private var startJob: Job? = null
    private val playback = RecordPlaybackDelegate()

    private val _uiState = MutableStateFlow(DiaryRecordUiState())
    val uiState: StateFlow<DiaryRecordUiState> = _uiState.asStateFlow()

    fun onEnter() {
        viewModelScope.launch {
            asrRepo.current() ?: _uiState.update { it.copy(asrNotConfigured = true) }
            metaRepo.touchActive()
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording || _uiState.value.isProcessing) return
        lastAudioFile = null
        _uiState.update {
            it.copy(
                isProcessing = true,
                elapsedMs = 0,
                transcript = null,
                savedId = null,
                topError = null,
                asrNotConfigured = false,
                networkFailed = false,
            )
        }

        startJob = viewModelScope.launch {
            val cfg = asrRepo.current()
            if (cfg == null || !cfg.isConfigured) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        asrNotConfigured = true,
                        topError = AppError.Code.ASR_NOT_CONFIGURED.userMessage,
                    )
                }
                return@launch
            }

            val asr = ServiceLocator.asrClient(cfg.asrProvider)
            val session = try {
                asr.openSession(cfg.asrKey()) { partial ->
                    _uiState.update { it.copy(transcript = partial) }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                handleStartFailure(t)
                return@launch
            }
            asrSession = session

            try {
                recorder.start(
                    frameListener = { frame -> session.appendAudio(frame) },
                    levelListener = { level -> _uiState.update { it.copy(audioLevel = level) } },
                )
            } catch (t: CancellationException) {
                session.close()
                throw t
            } catch (t: Throwable) {
                session.close()
                asrSession = null
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        topError = AppError.Code.RECORDING_FAILED.userMessage,
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isRecording = true, isProcessing = false) }
            startElapsedTicker()
        }
    }

    private fun handleStartFailure(t: Throwable) {
        asrSession?.close()
        asrSession = null
        _uiState.update {
            it.copy(
                isProcessing = false,
                topError = (t as? AppError)?.message ?: AppError.Code.UNKNOWN.userMessage,
            )
        }
    }

    private fun startElapsedTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                delay(200)
                val elapsed = recorder.elapsedMs()
                _uiState.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= 60_000L) {
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
        val session = asrSession
        asrSession = null
        if (file == null || !file.exists()) {
            session?.close()
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    topError = AppError.Code.RECORDING_FAILED.userMessage,
                )
            }
            return
        }
        lastAudioFile = file

        if (session == null) {
            processFile(file)
            return
        }

        viewModelScope.launch {
            val outcome = runCatching { session.finish() }
            session.close()
            val result = outcome.getOrElse { t ->
                handleProcessFailure(file, t)
                return@launch
            }
            saveResult(file, result)
        }
    }

    private fun processFile(file: File) {
        lastAudioFile = file
        _uiState.update { it.copy(isProcessing = true, networkFailed = false) }
        viewModelScope.launch {
            val cfg = asrRepo.current()
            if (cfg == null || !cfg.isConfigured) {
                file.delete()
                lastAudioFile = null
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        asrNotConfigured = true,
                        topError = AppError.Code.ASR_NOT_CONFIGURED.userMessage,
                    )
                }
                return@launch
            }

            val startedAt = System.currentTimeMillis()
            val asr = ServiceLocator.asrClient(cfg.asrProvider)
            val outcome = runCatching {
                asr.transcribe(apiKey = cfg.asrKey(), audioFile = file)
            }
            val result = outcome.getOrElse { t ->
                handleProcessFailure(file, t)
                return@launch
            }
            saveResult(file, result, System.currentTimeMillis() - startedAt)
        }
    }

    private suspend fun saveResult(
        file: File,
        result: com.elder.android.data.asr.AsrResult,
        latencyMs: Long = 0,
    ) {
        val cfg = asrRepo.current()
        val asr = ServiceLocator.asrClient(cfg?.asrProvider ?: com.elder.android.data.db.AsrProvider.BAILIAN)
        val text = result.text.trim()
        if (text.isBlank()) {
            handleProcessFailure(file, AppError.AsrEmptyTranscript())
            return
        }

        lastAudioFile = null
        val now = System.currentTimeMillis()
        val durationMs = _uiState.value.elapsedMs.toInt().coerceAtLeast(0)
        val id = diaryRepo.insert(
            DiaryEntryEntity(
                deviceId = "local",
                date = LocalDate.today(),
                text = text.take(200),
                transcript = text.take(500),
                source = DiaryEntryEntity.Source.ASR_ORIGINAL,
                audioPath = file.absolutePath,
                durationMs = durationMs,
                asrProvider = asr.providerRaw,
                asrModel = asr.model,
                asrConfidence = result.confidence,
                asrLatencyMs = latencyMs.toInt(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        _uiState.update {
            it.copy(
                isProcessing = false,
                transcript = text,
                savedId = id,
                networkFailed = false,
                audioLevel = 0f,
            )
        }
    }

    private fun handleProcessFailure(file: File, t: Throwable) {
        if (t is AppError.AsrAuthFailed) {
            file.delete()
            lastAudioFile = null
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    networkFailed = false,
                    topError = t.message,
                )
            }
            return
        }

        if (t is AppError.AsrEmptyTranscript) {
            file.delete()
            lastAudioFile = null
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    networkFailed = false,
                    topError = t.message,
                )
            }
            return
        }

        val message = (t as? AppError)?.message ?: AppError.Code.UNKNOWN.userMessage
        _uiState.update {
            it.copy(
                isProcessing = false,
                networkFailed = true,
                topError = message,
            )
        }
    }

    fun retry() {
        _uiState.update {
            it.copy(
                topError = null,
                transcript = null,
                savedId = null,
                networkFailed = false,
            )
        }
        startRecording()
    }

    fun retryAsr() {
        val file = lastAudioFile
        if (file != null && file.exists() && file.length() > 0) {
            _uiState.update { it.copy(networkFailed = false, topError = null) }
            processFile(file)
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
        startJob?.cancel()
        startJob = null
        tickerJob?.cancel()
        recorder.cancel()
        asrSession?.close()
        asrSession = null
        playback.release()
        _uiState.update {
            it.copy(
                isRecording = false,
                isProcessing = false,
                networkFailed = false,
                audioLevel = 0f,
                isPlaying = false,
                playheadMs = 0L,
                totalMs = 0L,
                playError = null,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        startJob?.cancel()
        startJob = null
        tickerJob?.cancel()
        recorder.cancel()
        asrSession?.close()
        asrSession = null
        playback.release()
    }

    /** 「已录音」状态下播放/暂停回听录音；委派 RecordPlaybackDelegate。
     *  设计 lock：MediaPlayer listener 主线程 + tickerActive 替代 mp.isPlaying + stopInternal 三步
     *  （沿用 docs/v0.11.x-bugfix.md §A.18 既有 lock） */
    fun togglePlay() {
        val s = _uiState.value
        val id = s.savedId ?: return
        viewModelScope.launch {
            val path = diaryRepo.audioPath(id) ?: run {
                _uiState.update { it.copy(playError = "找不到录音文件") }
                return@launch
            }
            val file = File(path)
            if (!file.exists()) {
                _uiState.update { it.copy(playError = "录音文件已丢失") }
                return@launch
            }
            playback.togglePlay(
                path = path,
                isCurrentlyPlaying = s.isPlaying,
                onState = { playing, head, total ->
                    _uiState.update { it.copy(isPlaying = playing, playheadMs = head, totalMs = total, playError = null) }
                },
                onError = { msg ->
                    _uiState.update { it.copy(isPlaying = false, playError = msg) }
                },
                tickerScope = viewModelScope,
            )
        }
    }

    /** 清除播放错误 Toast；与 dismissError() 区分（text 错误 vs 播放错误） */
    fun dismissPlayError() {
        _uiState.update { it.copy(playError = null) }
    }
}
