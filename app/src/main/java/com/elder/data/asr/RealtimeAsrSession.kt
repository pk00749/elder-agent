// §3.1.9 / §A.8 / §A.12：ASR Realtime 会话接口（Provider 中立）。
// 各 Provider 的 Client（千问百炼 / MiniMax Realtime）实现自己的 Session 类，
// 调用方只依赖本接口，避免耦合具体 Provider。
package com.elder.android.data.asr

/**
 * Realtime ASR 会话：用于流式录音场景（一边录一边识别）。
 *
 * 与 batch [AsrClient.transcribe] 不同：
 * - [appendAudio] 由录音回调驱动，每次送一段 PCM。
 * - [finish] 等服务端返回最终转写并构造 [AsrResult]。
 * - [close] 关闭 WebSocket 与 deferred。
 */
interface RealtimeAsrSession {
    /** 发送一段 PCM 16kHz mono 16-bit 音频帧给服务端（Base64 编码由实现类处理）。 */
    fun appendAudio(audio: ByteArray)

    /** 告诉服务端停止录音并等待最终转写；返回 [AsrResult]。 */
    suspend fun finish(): AsrResult

    /** 关闭 WebSocket；幂等。 */
    fun close()
}

/**
 * ASR Provider 中立接口（§A.14 ServiceLocator 工厂）：
 *  - [transcribe]：录音结束 → 一次性转写（ElderDiaryRecordViewModel 用）
 *  - [openSession]：边录边传（InterviewScreen 流式桥接用）
 *
 * Provider 切换时由 ServiceLocator.resolveAsr() 返回对应实现；
 * 调用方不感知 Provider 差异。
 */
interface AsrClient {
    suspend fun transcribe(apiKey: String, audioFile: java.io.File): AsrResult

    suspend fun openSession(
        apiKey: String,
        onPartial: (String) -> Unit = {},
    ): RealtimeAsrSession

    /** Provider 原始字符串（写库 §5.11 asr_config.asr_provider / diary_entry.asr_provider 用）。 */
    val providerRaw: String

    /** 当前 Provider 的 model 标识（写库 §5.11 asr_config.asr_model 用）。 */
    val model: String
}
