package com.elder.android.data.tts

interface TtsClient {
    suspend fun speak(apiKey: String, text: String): TtsResult

    fun stop()
}

data class TtsResult(
    val firstAudioDelayMs: Long,
    val totalLatencyMs: Long,
)
