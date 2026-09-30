// v0.11.0 行数说明：本文档 v0.11.0 加 LLMReplyToast 控制器 + voice-end-hint 控制器 + 
// save-export 注入到 saveDiary 后超出 AGENTS.md §18 500 行上限 50 行。
// 不拆出 InterviewToastController 子类的理由:showLlmReplyToast / dismissVoiceEndHint / saveDiary
// 都需要直接写 _state(MutableStateFlow),私有；抽出需把 _state 提到 outer 层破坏封装。
// 后续若再加职责(v0.12.0+),触发 §18 拆分点:把 Toast 控制 / voice hint 控制迁出,本文档降回 480 行以内。
// 对应 docs/v0.11.0.md §6(§18 例外)。


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
import com.elder.android.data.export.ElderSaveModeRepository
import com.elder.android.data.export.SaveExportRepository
import com.elder.android.data.export.SaveMode
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
    // v0.11.0 §3.1 / §3.2: 保存方式 prefs + 本地 / 云导出仓库
    private val saveModeRepo: ElderSaveModeRepository = ServiceLocator.saveModeRepo
    private val saveExportRepo: SaveExportRepository = ServiceLocator.saveExportRepo
    // v0.11.0 §3.3: Toast 显示时长(LLM 回复消失前在顶栏停留 2.5s)
    private val llmToastShowMs = 2_500L
    // v0.11.0 §3.3: 跟踪当前 Toast 消失任务;新 Toast 设置时取消上一个避免叠加
    private var llmToastDismissJob: Job? = null

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
            // v0.11.0 §3.4: 语音退出黄条显示条件 = prefs 未关闭 + 当前进入 READY
            val showVoiceHint = !saveModeRepo.isVoiceEndHintDismissed()
            _state.update {
                it.copy(
                    stage = InterviewStage.READY,
                    session = session,
                    assistantText = assistantHint,
                    needsConfig = config?.isConfigured != true,
                    showVoiceEndHint = showVoiceHint,
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

    /**
     * v0.11.0 §3.4 修订:加 onDone 参数用于「拜拜 / 够了」自动落库路径。
     * - 用户主动按「停止」: 传 onDone;若 Finalize 走 ELDER_EXPLICIT_END,TTS 播落幕语后自动 saveDiary → onDone 弹回主屏
     * - 60s 自动超时(`MAX_RECORD_MS`): onDone 默认为 {};若触发 voice-end 仍自动 saveDiary 但不弹回(屏幕停在 SAVED)
     */
    fun stopAndProcess(onDone: () -> Unit = {}) {
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
            processAudio(file, session, onDone)
        }
    }

    private suspend fun processAudio(
        file: File,
        liveSession: RealtimeAsrSession?,
        onDone: () -> Unit = {},
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
        // v0.10.0 §5: 删除 session.turns.size + 1 >= MAX_TURNS 硬截断;willFinalize 仅基于 isExplicitClose
        // 维度判定由 ChatAgent.respond() 内部 coveredDimensions 控制(此处不下判定)
        val willFinalize = com.elder.android.agent.AgentSafety.isExplicitClose(text)
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
                    speakOrShow(result.value.assistantText, showLlmReplyToast = true)
                }
                logTiming(asrLatencyMs, llmLatencyMs, ttsResult)
            }
            is AgentTurnResult.Finalize -> {
                val updated = result.value.session.attachAudio(file.absolutePath, _state.value.elapsedMs.toInt())
                interviewRepo.save(updated)
                // v0.11.0 §3.4: 落幕语优先于 summary 走 TTS
                val ttsSpoken = result.value.farewellText ?: result.value.summary
                val isVoiceEnd = result.value.farewellText != null
                _state.update {
                    it.copy(
                        stage = InterviewStage.SPEAKING,
                        session = updated,
                        assistantText = result.value.text,
                        draftText = result.value.text,
                        draftSummary = result.value.summary,
                        farewellText = result.value.farewellText,
                        pendingSaved = false,
                    )
                }
                // v0.11.0 G4 修订: voice-end 路径(老人说"拜拜 / 够了"等)跳过 REVIEW 直接 SAVED
                // — 老人主动结束意图已明确,等同手动按「✓ 保存」,不再要求二次确认
                val nextStage = if (isVoiceEnd) InterviewStage.SAVED else InterviewStage.REVIEW
                val ttsResult = speakOrShow(ttsSpoken, nextStage = nextStage)
                logTiming(asrLatencyMs, llmLatencyMs, ttsResult)
                if (isVoiceEnd) {
                    // 自动落库;若 text 为空 saveDiary 自己早退(不会破坏 review 兜底)
                    saveDiary(onDone)
                }
            }
        }
    }

    /**
     * TTS 播放一段文字 + 触发顶栏 Toast。
     *
     * v0.11.0 §3.3 新增:
     * - `showLlmReplyToast = true` 时同时把这段文字推到顶栏 Toast,2.5s 后自动消失
     * - 相同 text 重复调用早退(避免双 LLM 流并发时连续覆盖)
     * - finalize 落幕(review 阶段)走 showLlmReplyToast = true;OPENING 问候主动开问不显示(避免初次进来就盖信息)
     */
    private suspend fun speakOrShow(
        text: String,
        nextStage: InterviewStage = InterviewStage.READY,
        showLlmReplyToast: Boolean = true,
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
        if (showLlmReplyToast && text.isNotBlank()) showLlmReplyToast(text)
        return result.getOrNull()
    }

    /**
     * v0.11.0 §3.3: 显示顶栏 Toast;2.5s 后自动消失。
     * 相同 text 不重复显示;新 text 覆盖并重置 2.5s 计时。
     */
    private fun showLlmReplyToast(text: String) {
        val current = _state.value.llmReplyToastText
        if (current == text) return  // 幂等:不重复显示
        llmToastDismissJob?.cancel()
        _state.update { it.copy(llmReplyToastText = text) }
        llmToastDismissJob = viewModelScope.launch {
            delay(llmToastShowMs)
            _state.update { it.copy(llmReplyToastText = null) }
        }
    }

    /** v0.11.0 §3.3: 用户手动关掉 Toast(预留,目前由 2.5s 自动消失)。 */
    fun dismissLlmReplyToast() {
        llmToastDismissJob?.cancel()
        llmToastDismissJob = null
        _state.update { it.copy(llmReplyToastText = null) }
    }
    /**
     * v0.11.0 §3.4: 老人主动关闭语音退出黄条;同时持久化 prefs,后续不再弹。
     */
    fun dismissVoiceEndHint() {
        saveModeRepo.markVoiceEndHintDismissed()
        _state.update { it.copy(showVoiceEndHint = false) }
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
            val audioPath = session.turns.lastOrNull()?.audioPath.orEmpty()
            val diary = DiaryEntryEntity(
                deviceId = "local",
                date = LocalDate.today(),
                text = text,
                transcript = session.turns.joinToString("\n") { it.elderText }.take(500),
                summary = summary,
                sessionId = session.id,
                source = DiaryEntryEntity.Source.ASR_ORIGINAL,
                audioPath = audioPath,
                durationMs = session.turns.sumOf { it.durationMs },
                asrProvider = asr.providerRaw,
                asrModel = asr.model,
                asrConfidence = null,
                asrLatencyMs = null,
                createdAt = now,
                updatedAt = now,
            )
            val diaryId = diaryRepo.insert(diary)
            val saved = diary.copy(id = diaryId)
            interviewRepo.save(
                session.copy(status = InterviewStatus.SAVED, updatedAt = now)
            )
            // v0.11.0 §3.2: 按 SaveMode 触发本地 / 云导出;不阻塞 onDone() 回调
            val exportMode = saveModeRepo.current()
            val audioFile = if (audioPath.isNotBlank()) java.io.File(audioPath) else java.io.File("/dev/null")
            val outcome = saveExportRepo.exportIfNeeded(saved, audioFile, exportMode)
            Log.i(METRICS_TAG, "save export mode=$exportMode outcome=$outcome")
            _state.update { it.copy(stage = InterviewStage.SAVED, session = session.copy(status = InterviewStatus.SAVED)) }
            onDone()
        }
    }

    fun revise() {
        val session = _state.value.session ?: return
        // v0.10.0 §5: 删除 session.turns.size >= MAX_TURNS 硬截断;UiState.canRevise 已限 SAVED 状态
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
