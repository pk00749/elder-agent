// §3.1.9 + §5.11 asr_config 仓储：Room 持久化密文 + EncryptedSharedPreferences 保存 API Key
// v0.7.0 修订（§A.14）：ASR/TTS 双 Provider；4 份 Key（千问共用 / MiniMax LLM+ASR 共用 / MiniMax TTS 独立）
package com.elder.android.data

import android.content.Context
import com.elder.android.data.crypto.ApiKeyCipher
import com.elder.android.data.db.AsrConfigDao
import com.elder.android.data.db.AsrConfigEntity
import com.elder.android.data.asr.AsrProviderCatalog
import com.elder.android.data.db.AsrProvider
import com.elder.android.data.db.LlmProvider
import com.elder.android.data.llm.LlmProviderCatalog
import com.elder.android.data.db.ElderDatabase
import com.elder.android.data.db.TtsProvider
import com.elder.android.data.tts.TtsProviderCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** v0.7.0 §3.1.9 / §A.14：客户端 ASR / TTS 配置。 */
data class AsrConfig(
    val apiKey: String,                                          // 千问 ASR + TTS 共用
    val minimaxApiKey: String = "",                              // MiniMax LLM + MiniMax ASR 共用
    val ttsMinimaxApiKey: String = "",                           // MiniMax TTS 独立 Key
    val asrProvider: AsrProvider = AsrProvider.MINIMAX_REALTIME, // 0.7.0 默认 MiniMax
    val ttsProvider: TtsProvider = TtsProvider.MINIMAX,          // 0.7.0 默认 MiniMax
    val lastTestResult: String? = null,                          // 千问 ASR 测试结果
    val minimaxLastTestResult: String? = null,                   // MiniMax LLM 测试结果
    val ttsMinimaxLastTestResult: String? = null,                // MiniMax TTS 测试结果
    // 对应 prd.md §5.11 v0.8.0：LLM Provider 三选一
    val llmProvider: LlmProvider = LlmProvider.MINIMAX,          // 0.8.0 默认 MiniMax（回滚路径）
    val qwenLlmApiKey: String = "",                             // 千问 LLM Key；与 api_key 物理隔离
    val deepseekLlmApiKey: String = "",                         // DeepSeek LLM Key
    val qwenLlmLastTestResult: String? = null,
    val deepseekLlmLastTestResult: String? = null,
) {
    val isAsrConfigured: Boolean get() = asrKey().isNotBlank()
    val isLlmConfigured: Boolean get() = llmKey().isNotBlank()
    val isTtsConfigured: Boolean get() = ttsKey().isNotBlank()
    val isConfigured: Boolean get() = isAsrConfigured && isLlmConfigured && isTtsConfigured

    /** 当前 ASR Provider 需要的 Key；UI 只显示对应 Key 输入框。 */
    fun asrKey(): String = when (asrProvider) {
        AsrProvider.BAILIAN -> apiKey
        AsrProvider.MINIMAX_REALTIME -> minimaxApiKey
    }

    /** 当前 TTS Provider 需要的 Key。 */
    fun ttsKey(): String = when (ttsProvider) {
        TtsProvider.QWEN -> apiKey
        TtsProvider.MINIMAX -> ttsMinimaxApiKey
    }

    /** 当前 LLM Provider 需要的 Key；与 asrKey() / ttsKey() 同构（§A.15.3）。 */
    fun llmKey(): String = when (llmProvider) {
        LlmProvider.MINIMAX -> minimaxApiKey
        LlmProvider.QWEN -> qwenLlmApiKey
        LlmProvider.DEEPSEEK -> deepseekLlmApiKey
    }
}

class AsrConfigRepository(
    private val dao: AsrConfigDao,
    private val cipher: ApiKeyCipher,
) {
    fun observe(): Flow<AsrConfig?> = dao.observe().map { it?.toConfig() }

    suspend fun current(): AsrConfig? = dao.get()?.toConfig()

    suspend fun save(config: AsrConfig, now: Long = System.currentTimeMillis()) {
        if (config.apiKey.isNotBlank()) {
            cipher.encryptAndStore(ApiKeyCipher.KEY_API_KEY_ENC, config.apiKey)
        }
        if (config.minimaxApiKey.isNotBlank()) {
            cipher.encryptAndStore(ApiKeyCipher.KEY_MINIMAX_API_KEY_ENC, config.minimaxApiKey)
        }
        if (config.ttsMinimaxApiKey.isNotBlank()) {
            cipher.encryptAndStore(ApiKeyCipher.KEY_TTS_MINIMAX_API_KEY_ENC, config.ttsMinimaxApiKey)
        }
        if (config.qwenLlmApiKey.isNotBlank()) {
            cipher.encryptAndStore(ApiKeyCipher.KEY_QWEN_LLM_API_KEY_ENC, config.qwenLlmApiKey)
        }
        if (config.deepseekLlmApiKey.isNotBlank()) {
            cipher.encryptAndStore(ApiKeyCipher.KEY_DEEPSEEK_LLM_API_KEY_ENC, config.deepseekLlmApiKey)
        }
        dao.upsert(
            AsrConfigEntity(
                id = 1,
                apiKeyEnc = ApiKeyCipher.KEY_API_KEY_ENC,
                asrProvider = config.asrProvider.raw,
                asrEndpoint = AsrProviderCatalog.endpointOf(config.asrProvider),
                asrModel = AsrProviderCatalog.modelOf(config.asrProvider),
                ttsProvider = config.ttsProvider.raw,
                ttsEndpoint = TtsProviderCatalog.endpointOf(config.ttsProvider),
                ttsModel = TtsProviderCatalog.modelOf(config.ttsProvider),
                ttsVoiceId = TtsProviderCatalog.voiceIdOf(config.ttsProvider),
                minimaxApiKeyEnc = ApiKeyCipher.KEY_MINIMAX_API_KEY_ENC,
                ttsMinimaxApiKeyEnc = ApiKeyCipher.KEY_TTS_MINIMAX_API_KEY_ENC,
                updatedAt = now,
                lastTestResult = config.lastTestResult,
                minimaxLastTestResult = config.minimaxLastTestResult,
                ttsMinimaxLastTestResult = config.ttsMinimaxLastTestResult,
                llmProvider = config.llmProvider.raw,
                llmEndpoint = LlmProviderCatalog.endpointOf(config.llmProvider),
                llmModel = LlmProviderCatalog.modelOf(config.llmProvider),
                qwenLlmApiKeyEnc = ApiKeyCipher.KEY_QWEN_LLM_API_KEY_ENC,
                qwenLlmLastTestResult = config.qwenLlmLastTestResult,
                deepseekLlmApiKeyEnc = ApiKeyCipher.KEY_DEEPSEEK_LLM_API_KEY_ENC,
                deepseekLlmLastTestResult = config.deepseekLlmLastTestResult,
            )
        )
    }

    suspend fun clear() {
        dao.clear()
        cipher.clear(ApiKeyCipher.KEY_API_KEY_ENC)
        cipher.clear(ApiKeyCipher.KEY_MINIMAX_API_KEY_ENC)
        cipher.clear(ApiKeyCipher.KEY_TTS_MINIMAX_API_KEY_ENC)
        cipher.clear(ApiKeyCipher.KEY_QWEN_LLM_API_KEY_ENC)
        cipher.clear(ApiKeyCipher.KEY_DEEPSEEK_LLM_API_KEY_ENC)
    }

    private fun AsrConfigEntity.toConfig(): AsrConfig =
        AsrConfig(
            apiKey = cipher.decrypt(apiKeyEnc).orEmpty(),
            minimaxApiKey = minimaxApiKeyEnc?.let(cipher::decrypt).orEmpty(),
            ttsMinimaxApiKey = ttsMinimaxApiKeyEnc?.let(cipher::decrypt).orEmpty(),
            asrProvider = AsrProvider.fromRaw(asrProvider),
            ttsProvider = TtsProvider.fromRaw(ttsProvider),
            lastTestResult = lastTestResult,
            minimaxLastTestResult = minimaxLastTestResult,
            ttsMinimaxLastTestResult = ttsMinimaxLastTestResult,
            llmProvider = LlmProvider.fromRaw(llmProvider),
            qwenLlmApiKey = qwenLlmApiKeyEnc?.let(cipher::decrypt).orEmpty(),
            deepseekLlmApiKey = deepseekLlmApiKeyEnc?.let(cipher::decrypt).orEmpty(),
            qwenLlmLastTestResult = qwenLlmLastTestResult,
            deepseekLlmLastTestResult = deepseekLlmLastTestResult,
        )

    companion object {
        fun get(context: Context): AsrConfigRepository =
            AsrConfigRepository(
                ElderDatabase.get(context).asrConfigDao(),
                ApiKeyCipher(context),
            )
    }
}
