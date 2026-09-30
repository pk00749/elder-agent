// 对应 PRD §5.10 diary_entry_local（v3.0 MVP 本地 Room 表）
// v0.10.0 §6.3: 增量 5 列 oss_object_key / oss_sync_status / oss_synced_at / oss_last_error / oss_attempts
// §5.10 在锁定列表 → 仅 ALTER 追加,不动既有字段类型/默认值/必填。
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diary_entry_local",
    indices = [
        Index(value = ["date", "created_at"]),
        Index(value = ["device_id"]),
        Index(value = ["pending_id"], unique = true),
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: String,           // MVP 固定 "local"；§5.12 device_meta.device_id
    @ColumnInfo(name = "date") val date: String,                   // YYYY-MM-DD 按设备本地时区
    @ColumnInfo(name = "text") val text: String,                   // 日记正文 ≤ 200 字（v3.0 放宽到 200；v2.x 收紧到 100）
    @ColumnInfo(name = "transcript") val transcript: String? = null, // ASR 转写原文（v3.0 不可见，留给手动改写）
    @ColumnInfo(name = "summary") val summary: String? = null,      // 0.5.0 Agent 一句话摘要（≤ 60 字）
    @ColumnInfo(name = "session_id") val sessionId: String? = null, // 0.5.0 关联 interview_session.id
    @ColumnInfo(name = "pending_id") val pendingId: String? = null,  // 0.5.0 离线补做幂等键
    @ColumnInfo(name = "source") val source: Source,               // asr_original / asr_edited / manual
    @ColumnInfo(name = "audio_path") val audioPath: String,        // cacheDir/audio/{uuid}.m4a
    @ColumnInfo(name = "duration_ms") val durationMs: Int,
    @ColumnInfo(name = "asr_provider") val asrProvider: String? = null,
    @ColumnInfo(name = "asr_model") val asrModel: String? = null,
    @ColumnInfo(name = "asr_confidence") val asrConfidence: Float? = null,
    @ColumnInfo(name = "asr_latency_ms") val asrLatencyMs: Int? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,           // epoch ms
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,   // 软删除（§K.2f 老人端不提供删入口）

    // === v0.10.0 §6.3 增量字段（§5.10 在锁定列表;允许 ALTER TABLE 追加;不动既有字段） ===
    @ColumnInfo(name = "oss_object_key") val ossObjectKey: String? = null,       // 上传成功后写的对象 key;失败 / 未启用时 NULL
    @ColumnInfo(name = "oss_sync_status") val ossSyncStatus: String = "pending", // enum pending/syncing/synced/failed
    @ColumnInfo(name = "oss_synced_at") val ossSyncedAt: Long? = null,           // epoch ms
    @ColumnInfo(name = "oss_last_error") val ossLastError: String? = null,       // 失败时写错误码(OSS_AUTH_FAILED/OSS_NETWORK_ERROR/OSS_BUCKET_NOT_FOUND)
    @ColumnInfo(name = "oss_attempts") val ossAttempts: Int = 0,                 // 重试计数(<3 触发 retry,>=3 标 failed)
) {
    enum class Source { ASR_ORIGINAL, ASR_EDITED, MANUAL }
}
