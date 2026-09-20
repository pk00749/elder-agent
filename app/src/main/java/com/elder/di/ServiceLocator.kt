// 手动 DI（避免 Hilt 复杂度；ServiceLocator 暴露 MVP 全部依赖）
// v3.0 MVP：老人端独立运行，无服务端；只持有 Room DB + Audio + ASR client
// v0.7.0 修订（§A.14）：新增 minimaxAsr / minimaxTts；asrClient() / ttsClient() 工厂按当前配置返回对应 Provider
package com.elder.android.di

import android.content.Context
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfig
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.InterviewRepository
import com.elder.android.data.PendingDiaryRepository
import com.elder.android.data.PendingDiaryBackfill
import com.elder.android.data.RecentSummaryLoader
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.asr.AsrClient
import com.elder.android.data.asr.AsrProviderCatalog
import com.elder.android.data.asr.MiniMaxAsrClient
import com.elder.android.agent.AgentPrompts
import com.elder.android.agent.InterviewAgent
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.ElderFactRepository
import com.elder.android.data.db.TtsProvider
import com.elder.android.data.llm.LlmClientFactory
import com.elder.android.data.llm.MiniMaxClient
import com.elder.android.data.tts.MiniMaxTtsClient
import com.elder.android.data.tts.QwenTtsClient
import com.elder.android.data.tts.TtsClient
import com.elder.android.data.tts.TtsProviderCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceLocator {
    @Volatile private var inited = false

    lateinit var diaryRepo: DiaryRepository
        private set
    lateinit var asrConfigRepo: AsrConfigRepository
        private set
    lateinit var deviceMetaRepo: DeviceMetaRepository
        private set
    lateinit var interviewRepo: InterviewRepository
        private set
    lateinit var pendingDiaryRepo: PendingDiaryRepository
        private set
    lateinit var pendingBackfill: PendingDiaryBackfill
        private set
    lateinit var elderFactRepo: ElderFactRepository // v0.6.0 新增：长期事实仓储
        private set
    lateinit var recentSummaryLoader: RecentSummaryLoader // v0.6.0 新增：最近 N 天 summary loader
        private set
    lateinit var audioRecorder: AudioRecorder
        private set
    /** Bailian AsrClient 实例（Provider = bailian）；§A.8 实现。保留供旧调用方 + LiveTest。 */
    lateinit var asrApi: AsrApiClient
        private set
    /** MiniMax Realtime AsrClient 实例（Provider = minimax_realtime）；§A.12 实现。 */
    lateinit var minimaxAsr: MiniMaxAsrClient
        private set
    /** 千问 QwenTtsClient 实例（Provider = qwen）；§A.11 实现。 */
    lateinit var ttsClient: TtsClient
        private set
    /** MiniMax T2A TtsClient 实例（Provider = minimax）；§A.13 实现。 */
    lateinit var minimaxTts: MiniMaxTtsClient
        private set
    lateinit var interviewAgent: InterviewAgent
        private set
    lateinit var minimaxApi: MiniMaxClient
        private set
    /** v0.8.0 §A.15：LLM 客户端工厂，按 cfg.llmProvider 路由 MiniMax / Qwen / DeepSeek。 */
    lateinit var llmClientFactory: LlmClientFactory
        private set

    /**
     * v0.7.0 一次性 Toast 信号：Migration 4→5 升级后由 ServiceLocator.init 触发，
     * ElderHomeViewModel 监听后弹「0.7.0 升级：请到 AI 服务 重新配置 ASR 与 TTS」。
     * emit 一次后置 null。
     */
    private val _upgradeToast = MutableStateFlow<String?>(null)
    val upgradeToast: StateFlow<String?> = _upgradeToast.asStateFlow()

    fun consumeUpgradeToast() {
        _upgradeToast.value = null
    }

    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            val app = context.applicationContext
            diaryRepo = DiaryRepository.get(app)
            asrConfigRepo = AsrConfigRepository.get(app)
            deviceMetaRepo = DeviceMetaRepository.get(app)
            interviewRepo = InterviewRepository.get(app)
            pendingDiaryRepo = PendingDiaryRepository.get(app)
            elderFactRepo = ElderFactRepository.get(app)            // v0.6.0
            recentSummaryLoader = RecentSummaryLoader.get(app)       // v0.6.0
            audioRecorder = AudioRecorder(app)
            asrApi = AsrApiClient()
            minimaxAsr = MiniMaxAsrClient()
            ttsClient = QwenTtsClient()
            minimaxTts = MiniMaxTtsClient()
            minimaxApi = MiniMaxClient()
            // v0.8.0 §A.15：注入 LLM 客户端工厂；InterviewAgent 通过工厂按 cfg.llmProvider 路由
            llmClientFactory = LlmClientFactory()
            interviewAgent = InterviewAgent(                    // v0.6.0 加 elderFactRepo；v0.8.0 改 llm → llmFactory
                llmFactory = llmClientFactory,
                prompts = AgentPrompts(app),
                elderFactRepo = elderFactRepo,
            )
            pendingBackfill = PendingDiaryBackfill(
                configRepo = asrConfigRepo,
                pendingRepo = pendingDiaryRepo,
                diaryRepo = diaryRepo,
                asr = asrApi,
                agent = interviewAgent,
            )
            // v0.7.0：检测到 schema version 4→5 升级路径时弹一次性 Toast。
            detectUpgradeToast(app)
            inited = true
        }
    }

    private fun detectUpgradeToast(context: Context) {
        val prefs = context.getSharedPreferences("elder_v3_upgrade", Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt("db_version_seen", 0)
        if (currentVersion < 5) {
            prefs.edit().putInt("db_version_seen", 5).apply()
            _upgradeToast.value = "0.7.0 升级：请到 AI 服务 重新配置 ASR 与 TTS"
        }
    }

    /**
     * §A.14 ASR 工厂：根据当前 [AsrConfig.asrProvider] 返回对应 Client。
     * 默认 Provider = MINIMAX_REALTIME（v0.7.0 默认）。Provider 字段未持久化时
     * 走 [AsrProvider.fromRaw] 兜底回 BAILIAN（与既有数据兼容）。
     */
    suspend fun asrClient(): AsrClient {
        val provider = asrConfigRepo.current()?.asrProvider ?: AsrProvider.BAILIAN
        return AsrProviderCatalog.resolveClient(provider)
    }

    /** 同步版本：调用方已知当前 Provider；用于测试 + 不阻塞构造路径。 */
    fun asrClient(provider: AsrProvider): AsrClient =
        AsrProviderCatalog.resolveClient(provider)

    /** §A.14 TTS 工厂。 */
    suspend fun ttsClient(): TtsClient {
        val provider = asrConfigRepo.current()?.ttsProvider ?: TtsProvider.MINIMAX
        return TtsProviderCatalog.resolveClient(provider)
    }

    fun ttsClient(provider: TtsProvider): TtsClient =
        TtsProviderCatalog.resolveClient(provider)
}
