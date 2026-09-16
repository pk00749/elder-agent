package com.elder.android.screen.interview

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.agent.AgentTurnResult
import com.elder.android.agent.InterviewAgent
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.InterviewRepository
import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.InterviewTurn
import com.elder.android.data.PendingDiaryRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.db.DiaryEntryEntity
import com.elder.android.data.tts.TtsClient
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

class InterviewViewModel(app: Application) : AndroidViewModel(app) {
    private val configRepo: AsrConfigRepository = ServiceLocator.asrConfigRepo
    private val interviewRepo: InterviewRepository = ServiceLocator.interviewRepo
    private val pendingRepo: PendingDiaryRepository = ServiceLocator.pendingDiaryRepo
    private val diaryRepo: DiaryRepository = ServiceLocator.diaryRepo
    private val metaRepo = ServiceLocator.deviceMetaRepo
    private val recorder: AudioRecorder = ServiceLocator.audioRecorder
    private val asr: AsrApiClient = ServiceLocator.asrApi
    private val tts: TtsClient = ServiceLocator.ttsClient
    private val agent: InterviewAgent = ServiceLocator.interviewAgent

    private val _state = MutableStateFlow(InterviewUiState())
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private var config: AsrConfig? = null
    private var ttsEnabled: Boolean = true
    private var asrBridge: AsrSessionBridge? = null
    private var asrJob: Job? = null
    private var ticker: Job? = null
    private var work: Job? = null

    fun onEnter() {
        work?.cancel()
        work = viewModelScope.launch {
            config = configRepo.current()
            ttsEnabled = metaRepo.ensureInitialized().ttsEnabled
            val existing = interviewRepo.active()
            val session = existing ?: interviewRepo.create()
            if (existing?.status == InterviewStatus.REVIEWING) {
                _state.update {
                    it.copy(
                        stage = InterviewStage.REVIEW,
                        session = existing,
                        draftText = existing.draftText,
                        draftSummary = existing.draftSummary,
                        needsConfig = config?.isConfigured != true,
                    )
                }
                return@launch
            }
            _state.update {
                it.copy(
                    stage = InterviewStage.READY,
                    session = session,
                    assistantText = if (session.turns.isEmpty()) GREETING else session.turns.last().assistantText,
                    needsConfig = config?.isConfigured != true,
                )
            }
        }
    }

    fun startRecording() {
        val snapshot = _state.value
        if (snapshot.stage != InterviewStage.READY || snapshot.needsConfig) return
        val credentials = config ?: return
        work?.cancel()
        _state.update {
            it.copy(
                stage = InterviewStage.RECORDING,
                elapsedMs = 0,
                transcript = null,
                topError = null,
                pendingSaved = false,
            )
        }
        work = viewModelScope.launch {
            val bridge = AsrSessionBridge { partial ->
                _state.update { it.copy(transcript = partial) }
            }
            asrBridge = bridge
            try {
                recorder.start(bridge::onFrame)
            } catch (t: Throwable) {
                bridge.close()
                asrBridge = null
                fail(AppError.RecordingFailed(t), showConfigHint = false)
                return@launch
            }
            startTicker()
            asrJob = viewModelScope.launch {
                var connected: AsrApiClient.RealtimeAsrSession? = null
                try {
                    val opened = asr.openSession(credentials.apiKey, bridge::observePartial)
                    connected = opened
                    bridge.attach(opened)
                    connected = null
                } catch (e: AppError.AsrAuthFailed) {
                    bridge.close()
                    recorder.cancel()
                    ticker?.cancel()
                    asrBridge = null
                    fail(e)
                } catch (_: Throwable) {
                    // 保留本地音频，停止时走 full replay / pending。
                } finally {
                    connected?.close()
                }
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (_state.value.stage == InterviewStage.RECORDING) {
                delay(200)
                val elapsed = recorder.elapsedMs()
                _state.update { it.copy(elapsedMs = elapsed) }
                if (elapsed >= MAX_RECORD_MS) {
                    stopAndProcess()
                    break
                }
            }
        }
    }

    fun stopAndProcess() {
        if (_state.value.stage != InterviewStage.RECORDING) return
        ticker?.cancel()
        val file = recorder.stop()
        asrJob?.cancel()
        asrJob = null
        val session = asrBridge?.finish()
        asrBridge = null
        _state.update { it.copy(stage = InterviewStage.THINKING) }
        if (file == null || !file.exists() || file.length() <= WAV_HEADER_BYTES) {
            fail(AppError.RecordingFailed(), showConfigHint = false)
            return
        }
        work = viewModelScope.launch {
            processAudio(file, session)
        }
    }

    private suspend fun processAudio(
        file: File,
        liveSession: AsrApiClient.RealtimeAsrSession?,
    ) {
        val credentials = config ?: return fail(AppError.AsrNotConfigured())
        val asrStartedAt = System.currentTimeMillis()
        val asrResult = try {
            liveSession?.finish() ?: asr.transcribe(credentials.apiKey, file)
        } catch (t: CancellationException) {
            throw t
        } catch (e: AppError.AsrAuthFailed) {
            file.delete()
            return fail(e)
        } catch (_: Throwable) {
            runCatching { asr.transcribe(credentials.apiKey, file) }
                .getOrElse { return degrade(file) }
        } finally {
            liveSession?.close()
        }

        val text = asrResult.text.trim()
        if (text.isBlank()) return degrade(file)
        val asrLatencyMs = System.currentTimeMillis() - asrStartedAt
        _state.update { it.copy(transcript = text) }

        val session = _state.value.session ?: return degrade(file)
        val llmStartedAt = System.currentTimeMillis()
        val turnsWithCurrent = session.turns.map(InterviewTurn::elderText) + text
        val willFinalize = com.elder.android.agent.AgentSafety.isExplicitClose(text) ||
            com.elder.android.agent.AgentSafety.dimensionCount(turnsWithCurrent) >= 2 ||
            session.turns.size + 1 >= InterviewAgent.MAX_TURNS
        val speech = StreamingSpeechBuffer(
            apiKey = credentials.apiKey,
            enabled = ttsEnabled,
            speak = { segment -> tts.speak(credentials.apiKey, segment) },
            onFailure = { _state.update { it.copy(ttsFailed = true) } },
        )
        _state.update { it.copy(assistantText = "") }
        val result = runCatching {
            agent.respond(credentials.minimaxApiKey, session, text) { delta ->
                _state.update { it.copy(assistantText = it.assistantText.orEmpty() + delta) }
                speech.onDelta(delta, allowStreaming = !willFinalize)
            }
        }.getOrElse {
            return degrade(file)
        }
        val llmLatencyMs = System.currentTimeMillis() - llmStartedAt

        when (result) {
            is AgentTurnResult.Reply -> {
                val updated = result.value.session.attachAudio(file.absolutePath, _state.value.elapsedMs.toInt())
                interviewRepo.save(updated)
                _state.update {
                    it.copy(
                        session = updated,
                        assistantText = result.value.assistantText,
                        pendingSaved = false,
                    )
                }
                val ttsResult = if (speech.streamed) {
                    _state.update { it.copy(stage = InterviewStage.READY) }
                    null
                } else {
                    speakOrShow(result.value.assistantText)
                }
                logTiming(asrLatencyMs, llmLatencyMs, ttsResult)
            }
            is AgentTurnResult.Finalize -> {
                val updated = result.value.session.attachAudio(file.absolutePath, _state.value.elapsedMs.toInt())
                interviewRepo.save(updated)
                _state.update {
                    it.copy(
                        stage = InterviewStage.SPEAKING,
                        session = updated,
                        assistantText = result.value.text,
                        draftText = result.value.text,
                        draftSummary = result.value.summary,
                        pendingSaved = false,
                    )
                }
                val ttsResult = speakOrShow(result.value.summary, nextStage = InterviewStage.REVIEW)
                logTiming(asrLatencyMs, llmLatencyMs, ttsResult)
            }
        }
    }

    private suspend fun speakOrShow(
        text: String,
        nextStage: InterviewStage = InterviewStage.READY,
    ): com.elder.android.data.tts.TtsResult? {
        val key = config?.apiKey.orEmpty()
        val result: Result<com.elder.android.data.tts.TtsResult?> = if (ttsEnabled) {
            runCatching { tts.speak(key, text) }
        } else {
            Result.success(null)
        }
        _state.update {
            it.copy(
                stage = nextStage,
                ttsFailed = ttsEnabled && result.isFailure,
            )
        }
        return result.getOrNull()
    }

    private fun logTiming(
        asrLatencyMs: Long,
        llmLatencyMs: Long,
        tts: com.elder.android.data.tts.TtsResult?,
    ) {
        Log.i(
            METRICS_TAG,
            "asr_ms=$asrLatencyMs llm_ms=$llmLatencyMs " +
                "tts_total_ms=${tts?.totalLatencyMs ?: -1} tts_first_audio_ms=${tts?.firstAudioDelayMs ?: -1}",
        )
    }

    private suspend fun degrade(file: File) {
        val s = _state.value
        pendingRepo.enqueue(
            date = LocalDate.today(),
            audioPath = file.absolutePath,
            durationMs = s.elapsedMs.toInt(),
        )
        _state.update {
            it.copy(
                stage = InterviewStage.READY,
                pendingSaved = true,
                topError = "已保存录音，联网后会帮您整理",
                transcript = null,
            )
        }
    }

    fun saveDiary(onDone: () -> Unit) {
        val s = _state.value
        val session = s.session ?: return
        val text = s.draftText?.trim().orEmpty().take(InterviewAgent.MAX_TEXT_CHARS)
        val summary = s.draftSummary?.trim().orEmpty().take(InterviewAgent.MAX_SUMMARY_CHARS)
        if (text.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            diaryRepo.insert(
                DiaryEntryEntity(
                    deviceId = "local",
                    date = LocalDate.today(),
                    text = text,
                    transcript = session.turns.joinToString("\n") { it.elderText }.take(500),
                    summary = summary,
                    sessionId = session.id,
                    source = DiaryEntryEntity.Source.ASR_ORIGINAL,
                    audioPath = session.turns.lastOrNull()?.audioPath.orEmpty(),
                    durationMs = session.turns.sumOf { it.durationMs },
                    asrProvider = AsrApiClient.BAILIAN_PROVIDER,
                    asrModel = AsrApiClient.BAILIAN_MODEL,
                    asrConfidence = null,
                    asrLatencyMs = null,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            interviewRepo.save(
                session.copy(status = InterviewStatus.SAVED, updatedAt = now)
            )
            _state.update { it.copy(stage = InterviewStage.SAVED, session = session.copy(status = InterviewStatus.SAVED)) }
            onDone()
        }
    }

    fun revise() {
        val session = _state.value.session ?: return
        if (session.turns.size >= InterviewAgent.MAX_TURNS) return
        viewModelScope.launch {
            val updated = session.copy(
                status = InterviewStatus.ACTIVE,
                draftText = null,
                draftSummary = null,
                updatedAt = System.currentTimeMillis(),
            )
            interviewRepo.save(updated)
            _state.update {
                it.copy(
                    stage = InterviewStage.READY,
                    session = updated,
                    draftText = null,
                    draftSummary = null,
                )
            }
        }
    }

    fun dismissError() = _state.update { it.copy(topError = null, pendingSaved = false) }

    fun cancel() {
        ticker?.cancel()
        work?.cancel()
        runCatching { recorder.cancel() }
        asrJob?.cancel()
        asrJob = null
        asrBridge?.close()
        asrBridge = null
        tts.stop()
    }

    private fun fail(error: Throwable, showConfigHint: Boolean = false) {
        _state.update {
            it.copy(
                stage = InterviewStage.READY,
                topError = (error as? AppError)?.message ?: AppError.Code.UNKNOWN.userMessage,
                needsConfig = showConfigHint || it.needsConfig,
            )
        }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    companion object {
        const val GREETING = "今天都干啥了？"
        private const val MAX_RECORD_MS = 60_000L
        private const val WAV_HEADER_BYTES = 44L
        private const val METRICS_TAG = "InterviewMetrics"
    }
}

private fun InterviewSession.attachAudio(path: String, durationMs: Int): InterviewSession {
    if (turns.isEmpty()) return this
    return copy(
        turns = turns.dropLast(1) + turns.last().copy(audioPath = path, durationMs = durationMs),
    )
}
