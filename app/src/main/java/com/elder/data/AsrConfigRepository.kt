// §3.1.9 + §5.11 asr_config 仓储：Room 持久化密文 + EncryptedSharedPreferences 保存 API Key
// v3.0.1 §A.1.b：百炼硬编码 WorkspaceId/model，仓储只持有 apiKey
package com.elder.android.data

import android.content.Context
import com.elder.android.data.crypto.ApiKeyCipher
import com.elder.android.data.db.AsrConfigDao
import com.elder.android.data.db.AsrConfigEntity
import com.elder.android.data.db.ElderDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** v3.0.1：客户端 ASR 配置只剩百炼 API Key（PRD §A.1.b）。 */
data class AsrConfig(
    val apiKey: String,
    val lastTestResult: String?,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

class AsrConfigRepository(
    private val dao: AsrConfigDao,
    private val cipher: ApiKeyCipher,
) {
    fun observe(): Flow<AsrConfig?> = dao.observe().map { it?.toConfig() }

    suspend fun current(): AsrConfig? = dao.get()?.toConfig()

    suspend fun save(config: AsrConfig, now: Long = System.currentTimeMillis()) {
        cipher.encryptAndStore(config.apiKey)
        dao.upsert(
            AsrConfigEntity(
                id = 1,
                apiKeyEnc = ApiKeyCipher.KEY_API_KEY_ENC,
                updatedAt = now,
                lastTestResult = config.lastTestResult,
            )
        )
    }

    suspend fun clear() {
        dao.clear()
        cipher.clear()
    }

    private fun AsrConfigEntity.toConfig(): AsrConfig =
        AsrConfig(
            apiKey = cipher.decrypt(apiKeyEnc).orEmpty(),
            lastTestResult = lastTestResult,
        )

    companion object {
        fun get(context: Context): AsrConfigRepository =
            AsrConfigRepository(
                ElderDatabase.get(context).asrConfigDao(),
                ApiKeyCipher(context),
            )
    }
}
