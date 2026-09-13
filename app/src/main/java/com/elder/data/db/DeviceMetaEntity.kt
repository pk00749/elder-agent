// 对应 PRD §5.12 device_meta（v3.0 MVP 本机元数据，固定 id=1）
package com.elder.android.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_meta")
data class DeviceMetaEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "device_token") val deviceToken: String? = null,
    @ColumnInfo(name = "font_scale") val fontScale: FontScale,
    @ColumnInfo(name = "tts_enabled") val ttsEnabled: Boolean,
    @ColumnInfo(name = "timezone") val timezone: String,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "first_launch_at") val firstLaunchAt: Long,
    @ColumnInfo(name = "last_active_at") val lastActiveAt: Long,
)

enum class FontScale { DEFAULT, LARGE, XLARGE }
