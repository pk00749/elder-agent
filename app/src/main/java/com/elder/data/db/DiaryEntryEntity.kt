// 对应 PRD §5.10 diary_entry_local（v3.0 MVP 本地 Room 表）
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
    ],
)
data class DiaryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "device_id") val deviceId: String,           // MVP 固定 "local"；§5.12 device_meta.device_id
    @ColumnInfo(name = "date") val date: String,                   // YYYY-MM-DD 按设备本地时区
    @ColumnInfo(name = "text") val text: String,                   // 日记正文 ≤ 200 字（v3.0 放宽到 200；v2.x 收紧到 100）
    @ColumnInfo(name = "transcript") val transcript: String? = null, // ASR 转写原文（v3.0 不可见，留给手动改写）
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
) {
    enum class Source { ASR_ORIGINAL, ASR_EDITED, MANUAL }
}
