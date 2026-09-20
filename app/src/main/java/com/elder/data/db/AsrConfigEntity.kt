// 对应 PRD §5.11 asr_config（v0.7.0 16 列 schema）
// v0.7.0 修订：ASR/TTS 双 Provider 可切换；新增 asr_provider / asr_endpoint / asr_model /
// tts_provider / tts_endpoint / tts_model / tts_voice_id / tts_minimax_api_key_enc /
// tts_minimax_last_test_result。`asr_config` 不在 §18 锁定列表，Migration 4→5 走 DROP+CREATE。
// 字段约束：
//   - api_key_enc:        千问 ASR + 千问 TTS 共用
//   - minimax_api_key_enc: MiniMax LLM + MiniMax ASR 共用
//   - tts_minimax_api_key_enc: MiniMax TTS 独立 Key
//   - asr_provider:        "bailian" / "minimax_realtime"
//   - tts_provider:        "qwen" / "minimax"
//   - asr_endpoint / asr_model / tts_endpoint / tts_model / tts_voice_id: 客户端硬编码常量写库，UI 不暴露
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "asr_config")
data class AsrConfigEntity(
    @PrimaryKey val id: Int = 1,                                  // 单行表
    @ColumnInfo(name = "api_key_enc") val apiKeyEnc: String,        // Keystore-wrapped 密文；千问 ASR + TTS 共用
    @ColumnInfo(name = "asr_provider") val asrProvider: String,    // "bailian" | "minimax_realtime"
    @ColumnInfo(name = "asr_endpoint") val asrEndpoint: String,    // 客户端硬编码 endpoint（写库常量）
    @ColumnInfo(name = "asr_model") val asrModel: String,          // 客户端硬编码 model（写库常量）
    @ColumnInfo(name = "tts_provider") val ttsProvider: String,    // "qwen" | "minimax"
    @ColumnInfo(name = "tts_endpoint") val ttsEndpoint: String,    // 客户端硬编码 endpoint（写库常量）
    @ColumnInfo(name = "tts_model") val ttsModel: String,          // 客户端硬编码 model（写库常量）
    @ColumnInfo(name = "tts_voice_id") val ttsVoiceId: String?,    // MiniMax TTS 写入 "Cantonese_KindWoman"；千问 TTS 留空
    @ColumnInfo(name = "minimax_api_key_enc") val minimaxApiKeyEnc: String?,    // MiniMax LLM + ASR 共用
    @ColumnInfo(name = "tts_minimax_api_key_enc") val ttsMinimaxApiKeyEnc: String?,    // MiniMax TTS 独立 Key
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_test_result") val lastTestResult: String? = null,                  // 千问 ASR
    @ColumnInfo(name = "minimax_last_test_result") val minimaxLastTestResult: String? = null,   // MiniMax LLM
    @ColumnInfo(name = "tts_minimax_last_test_result") val ttsMinimaxLastTestResult: String? = null,    // MiniMax TTS
    // 对应 prd.md §5.11 v0.8.0 + AGENTS.md §A.15.3：LLM Provider 三选一
    @ColumnInfo(name = "llm_provider") val llmProvider: String = "minimax",           // "minimax" | "qwen" | "deepseek"
    @ColumnInfo(name = "llm_endpoint") val llmEndpoint: String? = null,               // Provider 硬编码 endpoint
    @ColumnInfo(name = "llm_model") val llmModel: String? = null,                     // Provider 硬编码 model
    @ColumnInfo(name = "qwen_llm_api_key_enc") val qwenLlmApiKeyEnc: String? = null,   // 千问 LLM Key 密文
    @ColumnInfo(name = "qwen_llm_last_test_result") val qwenLlmLastTestResult: String? = null,
    @ColumnInfo(name = "deepseek_llm_api_key_enc") val deepseekLlmApiKeyEnc: String? = null,    // DeepSeek LLM Key 密文
    @ColumnInfo(name = "deepseek_llm_last_test_result") val deepseekLlmLastTestResult: String? = null,
)

/** v0.7.0：客户端 ASR Provider 枚举（PRD §3.1.9 + §A.14）。 */
enum class AsrProvider(val raw: String) {
    BAILIAN("bailian"),
    MINIMAX_REALTIME("minimax_realtime");

    companion object {
        fun fromRaw(raw: String?): AsrProvider =
            entries.firstOrNull { it.raw == raw } ?: BAILIAN
    }
}

/** v0.7.0：客户端 TTS Provider 枚举（PRD §3.1.9 + §A.14）。 */
enum class TtsProvider(val raw: String) {
    QWEN("qwen"),
    MINIMAX("minimax");

    companion object {
        fun fromRaw(raw: String?): TtsProvider =
            entries.firstOrNull { it.raw == raw } ?: MINIMAX
    }
}
