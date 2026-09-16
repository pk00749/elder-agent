// 手动 DI（避免 Hilt 复杂度；ServiceLocator 暴露 MVP 全部依赖）
// v3.0 MVP：老人端独立运行，无服务端；只持有 Room DB + Audio + ASR client
package com.elder.android.di

import android.content.Context
import com.elder.android.audio.AudioRecorder
import com.elder.android.data.AsrConfigRepository
import com.elder.android.data.DeviceMetaRepository
import com.elder.android.data.DiaryRepository
import com.elder.android.data.InterviewRepository
import com.elder.android.data.PendingDiaryRepository
import com.elder.android.data.PendingDiaryBackfill
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.agent.AgentPrompts
import com.elder.android.agent.InterviewAgent
import com.elder.android.data.llm.MiniMaxClient
import com.elder.android.data.tts.QwenTtsClient
import com.elder.android.data.tts.TtsClient

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
    lateinit var audioRecorder: AudioRecorder
        private set
    lateinit var asrApi: AsrApiClient
        private set
    lateinit var ttsClient: TtsClient
        private set
    lateinit var interviewAgent: InterviewAgent
        private set
    lateinit var minimaxApi: MiniMaxClient
        private set

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
            audioRecorder = AudioRecorder(app)
            asrApi = AsrApiClient()
            ttsClient = QwenTtsClient()
            minimaxApi = MiniMaxClient()
            interviewAgent = InterviewAgent(
                llm = minimaxApi,
                prompts = AgentPrompts(app),
            )
            pendingBackfill = PendingDiaryBackfill(
                configRepo = asrConfigRepo,
                pendingRepo = pendingDiaryRepo,
                diaryRepo = diaryRepo,
                asr = asrApi,
                agent = interviewAgent,
            )
            inited = true
        }
    }
}
