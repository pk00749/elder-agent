package com.elder.android.data

import com.elder.android.agent.InterviewAgent
import com.elder.android.data.asr.AsrApiClient
import com.elder.android.data.db.DiaryEntryEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class PendingDiaryBackfill(
    private val configRepo: AsrConfigRepository,
    private val pendingRepo: PendingDiaryRepository,
    private val diaryRepo: DiaryRepository,
    private val asr: AsrApiClient,
    private val agent: InterviewAgent,
) {
    private val mutex = Mutex()

    suspend fun run() = mutex.withLock {
        val config = configRepo.current() ?: return
        if (!config.isConfigured) return
        pendingRepo.all().forEach { pending ->
            if (diaryRepo.findByPendingId(pending.id) != null) {
                pendingRepo.remove(pending.id)
                return@forEach
            }
            val file = File(pending.audioPath)
            if (!file.exists() || file.length() <= WAV_HEADER_BYTES) {
                pendingRepo.remove(pending.id)
                return@forEach
            }
            runCatching {
                val asrResult = asr.transcribe(config.apiKey, file)
                val transcript = asrResult.text.trim()
                require(transcript.isNotBlank())
                val (text, summary) = agent.summarize(config.minimaxApiKey, transcript)
                val now = System.currentTimeMillis()
                diaryRepo.insert(
                    DiaryEntryEntity(
                        deviceId = "local",
                        date = pending.date,
                        text = text.take(InterviewAgent.MAX_TEXT_CHARS),
                        transcript = transcript.take(500),
                        summary = summary.take(InterviewAgent.MAX_SUMMARY_CHARS),
                        pendingId = pending.id,
                        source = DiaryEntryEntity.Source.ASR_ORIGINAL,
                        audioPath = pending.audioPath,
                        durationMs = pending.durationMs,
                        asrProvider = AsrApiClient.BAILIAN_PROVIDER,
                        asrModel = AsrApiClient.BAILIAN_MODEL,
                        asrConfidence = asrResult.confidence,
                        asrLatencyMs = null,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            }.onSuccess {
                pendingRepo.remove(pending.id)
            }.onFailure { error ->
                pendingRepo.update(pending, error.message ?: error::class.java.simpleName)
            }
        }
    }

    companion object {
        private const val WAV_HEADER_BYTES = 44L
    }
}
