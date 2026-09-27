package com.elder.android.screen.interview

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elder.android.agent.AgentTurnResult
import com.elder.android.agent.DiarySummary
import com.elder.android.agent.ElderFact
import com.elder.android.agent.InterviewAgent
import com.elder.android.agent.SafetyAgent
import com.elder.android.agent.TimeOfDay
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.InterviewRepository
import com.elder.android.data.llm.LlmCredentials
import com.elder.android.data.InterviewSession
import com.elder.android.data.InterviewStatus
import com.elder.android.data.InterviewTurn
import com.elder.android.data.PendingDiaryRepository
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.asr.AsrClient
import com.elder.android.data.asr.RealtimeAsrSession
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.TtsProvider
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
    private val asr: AsrApiClient = ServiceLocator.asrApi   // 兜底 Provider=bailian；运行时按 config.asrProvider 解析
    // Bug fix：原本注入 ServiceLocator.ttsClient（lateinit property，init 时 hardcode 成 QwenTtsClient），
    // 运行时从来不按 config.ttsProvider 重新解析 → 选了 MiniMax TTS 仍然跑 QwenTtsClient + 千问 Key。
    // 改成 var，并在 onEnter() 里用 ServiceLocator.ttsClient() 函数（按 cfg.ttsProvider 路由）覆盖。
    private var tts: TtsClient = ServiceLocator.ttsClient
    private val agent: InterviewAgent = ServiceLocator.interviewAgent

    private val _state = MutableStateFlow(InterviewUiState())
    val state: StateFlow<InterviewUiState> = _state.asStateFlow()

    private var config: AsrConfig? = null
    private var ttsEnabled: Boolean = true
    private var asrBridge: AsrSessionBridge? = null
    private var asrJob: Job? = null
    private var ticker: Job? = null
    private var work: Job? = null

    // v0.6.0 注入：最近 3 天 diary summary + 长期事实表
    // PR2.3 仅加载暂存；PR2.4 接入 agent.respond(recentSummaries, elderFacts) 注入 system prompt。
    private var recentSummaries: List<DiarySummary> = emptyList()
    private var elderFacts: List<ElderFact> = emptyList()

    fun onEnter() {
        work?.cancel()
        work = viewModelScope.launch {
            config = configRepo.current()
            // Bug fix：按 config.ttsProvider 解析 TTS 客户端（不再 stale hardcode QwenTtsClient）
            tts = ServiceLocator.ttsClient()
            ttsEnabled = metaRepo.ensureInitialized().ttsEnabled
            // v0.6.0：进入访谈屏时并发加载 memory context（recent_summaries + elder_facts）
            // PR2.4 会把这两个字段传入 agent.respond(apiKey, session, text, recentSummaries, elderFacts)
            val today = LocalDate.today()
            recentSummaries = ServiceLocator.recentSummaryLoader.loadRecentSummaries(today)
            elderFacts = ServiceLocator.elderFactRepo.loadAllForInjection()
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
            val assistantHint = if (session.turns.isEmpty()) GREETING else session.turns.last().assistantText
            _state.update {
                it.copy(
                    stage = InterviewStage.READY,
                    session = session,
                    assistantText = assistantHint,
                    needsConfig = config?.isConfigured != true,
                )
            }

            // v0.9.0 主动开问：仅在 session 为空 + ASR/TTS/LLM Key 齐备时调 LLM 问候
            // 已有 turns 的旧会话（断电恢复）直接显示上次 assistantText，不重跑 open()
            if (session.turns.isNotEmpty()) return@launch
            val cfg = config
            if (cfg == null || !cfg.isConfigured) return@launch

            // 进入 OPENING 阶段（v0.9.0）：UI 显示进度条 + "让我先打个招呼…"
            _state.update { it.copy(stage = InterviewStage.OPENING) }

            val credentials = LlmCredentials(
                provider = cfg.llmProvider,
                minimaxApiKey = cfg.minimaxApiKey,
                qwenApiKey = cfg.qwenLlmApiKey,
                deepseekApiKey = cfg.deepseekLlmApiKey,
            )
            val greeting = runCatching {
                agent.open(
                    credentials = credentials,
                    timeOfDay = currentTimeOfDay(),
                    recentSummaries = recentSummaries,
                    elderFacts = elderFacts,
                )
            }.getOrElse { SafetyAgent.greetingFallback(currentTimeOfDay()) }

            // TTS 必播第一句（v0.9.0 主动开问核心）；失败沿用 §A.11.4 不重试
            if (ttsEnabled) {
                runCatching { tts.speak(cfg.ttsKey(), greeting) }
            }
            _state.update {
                it.copy(stage = InterviewStage.READY, assistantText = greeting)
            }
        }
    }

    /** v0.9.0 当前时段（MORNING 5-11 / NOON 12-17 / EVENING 18-4）。 */
    private fun currentTimeOfDay(): TimeOfDay {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.NOON
            else -> TimeOfDay.EVENING
        }
    }

    fun startRecording() {
        val snapshot = _state.value
        if (snapshot.stage != InterviewStage.READY || snapshot.needsConfig) return  // OPENING 阶段不接受录音
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
                var connected: RealtimeAsrSession? = null
                try {
                    val asr = ServiceLocator.asrClient(credentials.asrProvider)
                    val opened = asr.openSession(credentials.asrKey(), bridge::observePartial)
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
        liveSession: RealtimeAsrSession?,
    ) {
        val credentials = config ?: return fail(AppError.AsrNotConfigured())
        val asr = ServiceLocator.asrClient(credentials.asrProvider)
        val asrKey = credentials.asrKey()
        val asrStartedAt = System.currentTimeMillis()
        val asrResult = try {
            liveSession?.finish() ?: asr.transcribe(asrKey, file)
        } catch (t: CancellationException) {
            throw t
        } catch (e: AppError.AsrAuthFailed) {
            file.delete()
            return fail(e)
        } catch (_: Throwable) {
            runCatching { asr.transcribe(asrKey, file) }
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
        // v0.6.0：willFinalize 仅基于 isExplicitClose + turn 数（dimensionCount 已 deprecated）；
        // C1 维度判定由 InterviewAgent.respond() 内部 coveredDimensions 控制。
        val willFinalize = com.elder.android.agent.AgentSafety.isExplicitClose(text) ||
            session.turns.size + 1 >= InterviewAgent.MAX_TURNS
        val speech = StreamingSpeechBuffer(
            // Bug fix：TTS 走 ttsKey()（按 cfg.ttsProvider 选 apiKey / ttsMinimaxApiKey），
            // 而不是 credentials.apiKey（千问 Key），否则 MiniMax TTS 用千问 Key 永远 401。
            apiKey = credentials.ttsKey(),
            enabled = ttsEnabled,
            speak = { segment -> tts.speak(credentials.ttsKey(), segment) },
            onFailure = { _state.update { it.copy(ttsFailed = true) } },
        )
        _state.update { it.copy(assistantText = "") }
        // v0.8.0 §A.15：构造 LlmCredentials；provider 路由由 LlmClientFactory 完成
        val llmCredentials = LlmCredentials(
            provider = credentials.llmProvider,
            minimaxApiKey = credentials.minimaxApiKey,
            qwenApiKey = credentials.qwenLlmApiKey,
            deepseekApiKey = credentials.deepseekLlmApiKey,
        )
        val result = runCatching {
            agent.respond(
                credentials = llmCredentials,
                session = session,
                elderText = text,
                recentSummaries = recentSummaries,   // v0.6.0 §F1
                elderFacts = elderFacts,             // v0.6.0 §F4
            ) { delta ->
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
        // Bug fix：TTS 走 ttsKey()（按 cfg.ttsProvider 选 apiKey / ttsMinimaxApiKey），
        // 而不是 config.apiKey（千问 Key）。
        val key = config?.ttsKey().orEmpty()
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
                    asrProvider = asr.providerRaw,
                    asrModel = asr.model,
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
