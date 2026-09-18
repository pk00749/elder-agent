// §3.1.9 / §A.13 / §A.14：TTS Provider 集中表（endpoint / model / voice_id / provider raw）
// 新增 Provider 时：实现 Client + 在此注册即可。
package com.elder.android.data.tts

import com.elder.android.data.db.TtsProvider

/**
 * TTS Provider → endpoint / model / voice_id 映射。
 * voice_id 在 MiniMax TTS 上硬编码为 `Cantonese_KindWoman`（§A.13.1），UI 不暴露。
 */
object TtsProviderCatalog {
    fun endpointOf(p: TtsProvider): String = when (p) {
        TtsProvider.QWEN -> QwenTtsClient.REALTIME_ENDPOINT
        TtsProvider.MINIMAX -> MiniMaxTtsClient.WS_URL
    }

    fun modelOf(p: TtsProvider): String = when (p) {
        TtsProvider.QWEN -> QwenTtsClient.MODEL
        TtsProvider.MINIMAX -> MiniMaxTtsClient.MINIMAX_TTS_MODEL
    }

    fun voiceIdOf(p: TtsProvider): String? = when (p) {
        TtsProvider.QWEN -> QwenTtsClient.VOICE
        TtsProvider.MINIMAX -> MiniMaxTtsClient.MINIMAX_TTS_VOICE_ID
    }

    fun providerRaw(p: TtsProvider): String = p.raw

    /** ServiceLocator.ttsClient() 工厂入口（§A.14）。 */
    fun resolveClient(provider: TtsProvider): TtsClient = when (provider) {
        TtsProvider.QWEN -> QwenTtsClient()
        TtsProvider.MINIMAX -> MiniMaxTtsClient()
    }
}
