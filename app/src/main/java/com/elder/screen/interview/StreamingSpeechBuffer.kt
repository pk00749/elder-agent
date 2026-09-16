package com.elder.android.screen.interview

import com.elder.android.data.tts.TtsResult

/**
 * 把 LLM 的增量文本按完整句切给 TTS，避免等待全文；失败时由调用方走文字兜底。
 */
class StreamingSpeechBuffer(
    private val apiKey: String,
    private val enabled: Boolean,
    private val speak: suspend (String) -> TtsResult,
    private val onFailure: () -> Unit,
) {
    private val buffer = StringBuilder()
    private var spokenChars = 0

    var streamed: Boolean = false
        private set

    suspend fun onDelta(delta: String, allowStreaming: Boolean) {
        buffer.append(delta)
        if (!enabled || !allowStreaming) return
        val boundary = buffer.indexOfLast { it in SENTENCE_ENDS }
        if (boundary < spokenChars) return
        val segment = buffer.substring(spokenChars, boundary + 1).trim()
        if (segment.isBlank()) return
        streamed = true
        spokenChars = boundary + 1
        runCatching { speak(segment) }.onFailure { onFailure() }
    }

    companion object {
        private val SENTENCE_ENDS = setOf('。', '！', '？', '!', '?')
    }
}
