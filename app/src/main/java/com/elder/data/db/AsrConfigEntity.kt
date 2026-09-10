// 对应 PRD §5.11 asr_config（v3.0 MVP 单行表，固定 id=1）
// v3.0.1 简化：百炼硬编码 WorkspaceId + model，只保留 api_key 密文（§A.1.b）
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "asr_config")
data class AsrConfigEntity(
    @PrimaryKey val id: Int = 1,                                  // 单行表
    @ColumnInfo(name = "api_key_enc") val apiKeyEnc: String,        // Keystore-wrapped 密文
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_test_result") val lastTestResult: String? = null,
)

/** v3.0.1：客户端 ASR 唯一上游（PRD §A.1.b）。 */
enum class AsrProvider(val raw: String) {
    BAILIAN("bailian");

    companion object {
        fun fromRaw(raw: String): AsrProvider =
            entries.firstOrNull { it.raw == raw } ?: BAILIAN
    }
}
